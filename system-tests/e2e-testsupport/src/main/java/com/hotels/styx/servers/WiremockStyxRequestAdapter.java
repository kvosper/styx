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

import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.Cookie;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.HttpRequest;

import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.http.HttpHeader.httpHeader;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

public class WiremockStyxRequestAdapter implements Request {
    private final HttpRequest styxRequest;

    public WiremockStyxRequestAdapter(HttpRequest styxRequest) {
        this.styxRequest = requireNonNull(styxRequest);
    }

    @Override
    public String getUrl() {
        return styxRequest.path();
    }

    @Override
    public String getAbsoluteUrl() {
        String host = styxRequest.header(HOST).orElse("");
        String protocol = "http";

        return protocol + "://" + host + styxRequest.url().toURI().toString();
    }

    @Override
    public RequestMethod getMethod() {
        return RequestMethod.fromString(styxRequest.method().name());
    }

    @Override
    public String getScheme() {
        return null;
    }

    @Override
    public String getHost() {
        return null;
    }

    @Override
    public int getPort() {
        return 0;
    }

    @Override
    public String getClientIp() {
        return null;
    }

    @Override
    public String getHeader(String key) {
        return styxRequest.header(key).orElse(null);
    }

    @Override
    public HttpHeader header(String key) {
        List<String> values = styxRequest.headers(key);
        return httpHeader(key, values.toArray(new String[values.size()]));
    }

    @Override
    public ContentTypeHeader contentTypeHeader() {
        return styxRequest.header(CONTENT_TYPE)
                .map(ContentTypeHeader::new)
                .orElseGet(ContentTypeHeader::absent);
    }

    @Override
    public HttpHeaders getHeaders() {
        List<HttpHeader> list = stream(styxRequest.headers().spliterator(), false)
                .map(styxHeader -> httpHeader(styxHeader.name(), styxHeader.value()))
                .collect(toList());

        return new HttpHeaders(list);
    }

    @Override
    public boolean containsHeader(String key) {
        return styxRequest.headers().contains(key);
    }

    @Override
    public Set<String> getAllHeaderKeys() {
        return new HashSet<>(styxRequest.headers().names());
    }

    @Override
    public Map<String, Cookie> getCookies() {
        return null;
    }

    @Override
    public QueryParameter queryParameter(String key) {
        return styxRequest.queryParam(key)
                .map(value -> new QueryParameter(key, ImmutableList.of(value)))
                .orElseGet(() -> QueryParameter.absent(key));
    }

    @Override
    public byte[] getBody() {
        return styxRequest.body();
    }

    @Override
    public String getBodyAsString() {
        return styxRequest.bodyAs(UTF_8);
    }

    @Override
    public String getBodyAsBase64() {
        return Base64.getEncoder().encodeToString(styxRequest.body());
    }

    @Override
    public boolean isMultipart() {
        return false;
    }

    @Override
    public Collection<Part> getParts() {
        return Collections.emptyList();
    }

    @Override
    public Part getPart(String name) {
        return null;
    }

    @Override
    public boolean isBrowserProxyRequest() {
        return false;
    }

    @Override
    public Optional<Request> getOriginalRequest() {
        return Optional.absent();
    }
}
