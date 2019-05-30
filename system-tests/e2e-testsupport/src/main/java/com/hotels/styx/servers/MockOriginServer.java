/*
  Copyright (C) 2013-2019 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.servers;

import com.github.tomakehurst.wiremock.admin.AdminRoutes;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.core.MappingsSaver;
import com.github.tomakehurst.wiremock.core.WireMockApp;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.global.GlobalSettingsHolder;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.BasicResponseRenderer;
import com.github.tomakehurst.wiremock.http.HttpResponder;
import com.github.tomakehurst.wiremock.http.ProxyResponseRenderer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestHandler;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.http.StubResponseRenderer;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.security.Authenticator;
import com.github.tomakehurst.wiremock.standalone.MappingsLoader;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.stubbing.StubMappings;
import com.github.tomakehurst.wiremock.verification.InMemoryRequestJournal;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.github.tomakehurst.wiremock.verification.RequestJournal;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ServiceManager;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.HttpServers;
import com.hotels.styx.server.HttpsConnectorConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.core.WireMockApp.FILES_ROOT;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.google.common.base.Optional.absent;
import static com.hotels.styx.servers.WiremockResponseConverter.toStyxResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;

public final class MockOriginServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MockOriginServer.class);
    private static final int MAX_CONTENT_LENGTH = 256 * 1024;

    private final String appId;
    private final String originId;
    private final int adminPort;

    private int serverPort;
    private final HttpServer adminServer;
    private final HttpServer mockServer;

    static {
        System.setProperty("org.mortbay.log.class", "com.github.tomakehurst.wiremock.jetty.LoggerAdapter");
    }

    private ServiceManager services;

    private MockOriginServer(String appId, String originId, int adminPort, int serverPort, HttpServer adminServer, HttpServer mockServer) {
        this.appId = appId;
        this.originId = originId;
        this.adminPort = adminPort;
        this.serverPort = serverPort;
        this.adminServer = adminServer;
        this.mockServer = mockServer;
    }

    public static MockOriginServer create(String appId, String originId, int adminPort, HttpConnectorConfig httpConfig) {
        WireMockApp wireMockApp = wireMockApp();
        HttpServer adminServer = createAdminServer(originId, adminPort, wireMockApp);
        HttpServer mockServer = HttpServers.createHttpServer(
                "mock-stub-" + originId,
                httpConfig,
                mockHandler(originId, wireMockApp, new WireMockConfiguration()));
        int serverPort = httpConfig.port();

        return new MockOriginServer(appId, originId, adminPort, serverPort, adminServer, mockServer);
    }

    public static MockOriginServer create(String appId, String originId, int adminPort, HttpsConnectorConfig httpsConfig) {
        WireMockApp wireMockApp = wireMockApp();
        HttpServer adminServer = createAdminServer(originId, adminPort, wireMockApp);
        HttpServer mockServer = HttpServers.createHttpsServer(
                "mock-stub-" + originId,
                httpsConfig,
                mockHandler(originId, wireMockApp, new WireMockConfiguration()));
        int serverPort = httpsConfig.port();

        return new MockOriginServer(appId, originId, adminPort, serverPort, adminServer, mockServer);
    }


    private static HttpHandler mockHandler(String originId, WireMockApp wireMockApp, WireMockConfiguration defaultConfig) {
        return newHandler(originId, new StubRequestHandler(
                wireMockApp,
                new StubResponseRenderer(
                        defaultConfig.filesRoot().child(FILES_ROOT),
                        wireMockApp.getGlobalSettingsHolder(),
                        new ProxyResponseRenderer(
                                defaultConfig.proxyVia(),
                                defaultConfig.httpsSettings().trustStore(),
                                defaultConfig.shouldPreserveHostHeader(),
                                defaultConfig.proxyHostHeader(),
                                wireMockApp.getGlobalSettingsHolder()
                        ),
                        Collections.emptyList()
                ),
                wireMockApp,
                emptyMap(),
                new InMemoryRequestJournal(Optional.absent())
        ));
    }

    //ProxySettings proxySettings, KeyStoreSettings trustStoreSettings, boolean preserveHostHeader, String hostHeaderValue, GlobalSettingsHolder globalSettingsHolder

    private static HttpHandler newHandler(String originId, RequestHandler wireMockHandler) {
        return (httpRequest, ctx) ->
                httpRequest.aggregate(MAX_CONTENT_LENGTH)
                        .map(fullRequest -> {
                            LOGGER.info("{} received: {}\n{}", new Object[]{originId, fullRequest.url(), fullRequest.body()});
                            return fullRequest;
                        })
                        .flatMap(fullRequest -> {
                            Request wmRequest = new WiremockStyxRequestAdapter(fullRequest);
                            AtomicReference<com.github.tomakehurst.wiremock.http.Response> ar = new AtomicReference<>();

                            // TODO is this used synchronously or asynchronously? I have implemented it assuming synchronous
                            HttpResponder httpResponder = (request, response) -> {
                                ar.set(response);
                            };
                            wireMockHandler.handle(wmRequest, httpResponder);
                            return Eventual.of(toStyxResponse(ar.get()).stream());
                        });
    }

    private static ByteBuf toByteBuf(String string) {
        return Unpooled.copiedBuffer(string, UTF_8);
    }

    public MockOriginServer start() {
        services = new ServiceManager(ImmutableList.of(adminServer, mockServer));
        services.startAsync().awaitHealthy();
        return this;
    }

    public MockOriginServer stop() {
        services.stopAsync().awaitStopped();
        return this;
    }

    public MockOriginServer stub(UrlPattern urlMatchingStrategy, ResponseDefinitionBuilder response) {
        WireMock wm = new WireMock("localhost", adminPort());
        wm.register(WireMock.get(urlMatchingStrategy).willReturn(response));
        return this;
    }

    public MockOriginServer stub(MappingBuilder mappingBuilder, ResponseDefinitionBuilder response) {
        configureFor("localhost", adminPort());
        stubFor(mappingBuilder.willReturn(response));
        return this;
    }

    public void verify(int count, RequestPatternBuilder builder) {
        WireMock wm = new WireMock("localhost", adminPort());
        wm.verifyThat(count, builder);
    }

    public void verify(RequestPatternBuilder builder) {
        WireMock wm = new WireMock("localhost", adminPort());
        wm.verifyThat(builder);
    }

    public MockOriginServer reset() {
        WireMock wm = new WireMock("localhost", adminPort());
        wm.resetMappings();
        return this;
    }

    public String appId() {
        return appId;
    }

    public String originId() {
        return originId;
    }

    public int port() {
        if (mockServer.httpAddress() != null) {
            return mockServer.httpAddress().getPort();
        } else {
            return mockServer.httpsAddress().getPort();
        }
    }

    public int adminPort() {
        return adminServer.httpAddress().getPort();
    }

    public boolean isRunning() {
        return services.isHealthy();
    }

    // NO-OP
    private static WireMockApp wireMockApp() {
        return new WireMockApp(false,
                stubMappings -> {

                },
                new MappingsSaver(){
                    @Override
                    public void save(List<StubMapping> stubMappings) {
                    }

                    @Override
                    public void save(StubMapping stubMapping) {
                    }

                    @Override
                    public void remove(StubMapping stubMapping) {
                    }

                    @Override
                    public void removeAll() {
                    }
                },
                false,
                absent(),
                emptyMap(),
                emptyMap(),
                null,
                null
        );
    }

    /*
    boolean browserProxyingEnabled,
        MappingsLoader defaultMappingsLoader,
        MappingsSaver mappingsSaver,
        boolean requestJournalDisabled,
        Optional<Integer> maxRequestJournalEntries,
        Map<String, ResponseDefinitionTransformer> transformers,
        Map<String, RequestMatcherExtension> requestMatchers,
        FileSource rootFileSource,
        Container container
     */

    private static HttpServer createAdminServer(String originId, int adminPort, WireMockApp wireMockApp) {
        return HttpServers.createHttpServer(
                "mock-admin-" + originId,
                new HttpConnectorConfig(adminPort),
                adminHandler(wireMockApp));
    }

    private static HttpHandler adminHandler(WireMockApp admin) {
        AdminRoutes adminRoutes = AdminRoutes.defaults();
        Authenticator authenticator = request -> true;
        boolean requireHttps = false;

        return newHandler(new AdminRequestHandler(adminRoutes, admin, new BasicResponseRenderer(), authenticator, requireHttps));
    }

    private static HttpHandler newHandler(RequestHandler wireMockHandler) {
        return (httpRequest, ctx) ->
                httpRequest.aggregate(MAX_CONTENT_LENGTH)
                        .map(fullRequest -> {
                            LOGGER.info("Received: {}\n{}", new Object[]{fullRequest.url(), fullRequest.body()});
                            return fullRequest;
                        })
                        .flatMap(fullRequest -> {
                            Request wmRequest = new WiremockStyxRequestAdapter(fullRequest);

                            AtomicReference<com.github.tomakehurst.wiremock.http.Response> ar = new AtomicReference<>();

                            // TODO is this used synchronously or asynchronously? I have implemented it assuming synchronous
                            HttpResponder httpResponder = (request, response) -> {
                                ar.set(response);
                            };
                            wireMockHandler.handle(wmRequest, httpResponder);
                            return Eventual.of(toStyxResponse(ar.get()).stream());
                        });
    }
}
