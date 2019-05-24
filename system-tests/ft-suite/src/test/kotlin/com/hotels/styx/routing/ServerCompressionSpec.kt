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
package com.hotels.styx.routing

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.hotels.styx.StyxConfig
import com.hotels.styx.StyxServer
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.startup.StyxServerComponents
import com.hotels.styx.support.ResourcePaths.fixturesHome
import io.kotlintest.Spec
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Files.createTempFile
import java.nio.file.Files.write
import java.util.*
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.zip.GZIPOutputStream

class ServerCompressionSpec : StringSpec() {
    val origin1 = WireMockServer(wireMockConfig().dynamicPort())

    val originsFile = createTempFile("origins-", ".yml")

    val yamlText = """
        services:
          factories:
            backendServiceRegistry:
              class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
              config: {originsFile: "$originsFile"}

        admin:
          connectors:
            http:
              port: 0
      """.trimIndent()

    init {
        origin1.start()

        configureFor(origin1.port())
        stubFor(WireMock.get(urlPathEqualTo("/")).willReturn(aResponse()
                .withBody("Content")
                .withStatus(OK.code())))

        val content = """
            ---
            - id: "app"
              path: "/"
              connectionPool:
                maxConnectionsPerHost: 45
                maxPendingConnectionsPerHost: 15
                connectTimeoutMillis: 1000
                pendingConnectionTimeoutMillis: 8000
              healthCheck:
                uri: "/version.txt"
                intervalMillis: 5000
              responseTimeoutMillis: 60000
              origins:
              - { id: "app1", host: "localhost:${origin1.port()}" }
        """.trimIndent()

        write(originsFile, content.toByteArray())

        "Does a thing" {
            val request = get("/")
                    .header(HOST, "${styxServer.proxyHttpAddress().hostName}:${styxServer.proxyHttpAddress().port}")
                    .header("Accept-Encoding", "gzip,deflate")
                    .build();

            val response = client.send(request)
                    .toMono()
                    .block();

            response?.status() shouldBe (OK)
            response?.header("Content-Encoding") shouldBe Optional.of("gzip")
            // TODO how to check compressed content?
            response?.bodyAs(UTF_8) shouldBe ("Content")
        }
    }

    private fun compress(string: String): Any {
        val baos = ByteArrayOutputStream()
        val gzos = GZIPOutputStream(baos)
        gzos.bufferedWriter().write(string)

        val compressed = String(baos.toByteArray(), UTF_8)

        return compressed
    }

    val client: StyxHttpClient = StyxHttpClient.Builder()
            .threadName("functional-test-client")
            .connectTimeout(1000, MILLISECONDS)
            .maxHeaderSize(2 * 8192)
            .build()

    val styxServer = StyxServer(StyxServerComponents.Builder()
            .styxConfig(StyxConfig.fromYaml(yamlText))
            .build())

    override fun beforeSpec(spec: Spec) {
        styxServer.startAsync().awaitRunning()
    }

    override fun afterSpec(spec: Spec) {
        styxServer.stopAsync().awaitTerminated()
        Files.deleteIfExists(originsFile)
    }
}
