/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.client

import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.client.netty.connectionpool.HttpRequestOperation
import com.hotels.styx.common.format.DefaultHttpMessageFormatter
import com.hotels.styx.common.format.HttpMessageFormatter

/**
 * A Factory for creating an HttpRequestOperation from an LiveHttpRequest.
 */
fun interface HttpRequestOperationFactory {
    /**
     * Create a new operation for handling the http request.
     *
     * @param request the http request
     * @return a new http operation
     */
    fun newHttpRequestOperation(request: LiveHttpRequest): HttpRequestOperation

    /**
     * Builds HttpRequestOperationFactory objects.
     */
    class Builder {
        var originStatsFactory: OriginStatsFactory? = null
        var responseTimeoutMillis = 60000
        var flowControlEnabled = false
        var requestLoggingEnabled = false
        var longFormat = false
        var httpMessageFormatter: HttpMessageFormatter = DefaultHttpMessageFormatter()

        fun originStatsFactory(factory: OriginStatsFactory) = apply {
            originStatsFactory = factory
        }

        fun responseTimeoutMillis(responseTimeoutMillis: Int) = apply {
            this.responseTimeoutMillis = responseTimeoutMillis
        }

        fun flowControlEnabled(flowControlEnabled: Boolean) = apply {
            this.flowControlEnabled = flowControlEnabled
        }

        fun requestLoggingEnabled(requestLoggingEnabled: Boolean) = apply {
            this.requestLoggingEnabled = requestLoggingEnabled
        }

        fun longFormat(longFormat: Boolean) = apply {
            this.longFormat = longFormat
        }

        fun httpMessageFormatter(httpMessageFormatter: HttpMessageFormatter) = apply {
            this.httpMessageFormatter = httpMessageFormatter
        }

        fun build(): HttpRequestOperationFactory = HttpRequestOperationFactory { request ->
            HttpRequestOperation(
                request,
                originStatsFactory,
                responseTimeoutMillis,
                requestLoggingEnabled,
                longFormat,
                httpMessageFormatter
            )
        }

        companion object {
            @JvmStatic
            fun httpRequestOperationFactoryBuilder(): Builder {
                return Builder()
            }
        }
    }
}