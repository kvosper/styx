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
package com.hotels.styx.metrics

import com.hotels.styx.api.Metrics
import com.hotels.styx.api.metrics.MeterFactory
import io.micrometer.core.instrument.*
import java.util.concurrent.ConcurrentMap

class CentralisedMetrics(val registry: MeterRegistry) {
    /* How about:
         * a metric for responses. includes status, and anything else etc.
            * cannot combine latency because it is a different metric type - timer instead of counter
         * separate metrics for origin behaviours, designed not to get confused
     */

    fun countResponse(code: Int) {
        val tags: Tags = if (code in 100..599) {
            Tags.of("statusClass", (code / 100).toString() + "xx")
                .and("statusCode", code.toString())
        } else {
            Tags.of("statusClass", "unrecognised")
                .and("statusCode", "unrecognised")
        }

        registry.counter("proxy.response", tags).increment()
    }

    fun countRequestReceived() {
        registry.counter("proxy.request.received").increment()
    }

    fun requestLatencyTimer(): Timer = MeterFactory.timer(registry, Metrics.name("proxy.request.latency"))

    fun startTiming(): Timer.Sample = Timer.start(registry)

    fun registerOutstandingRequestsGauge(ongoingRequests: ConcurrentMap<Any, Timer.Sample>) {
        registry.gauge("proxy.request.outstanding", ongoingRequests) {
            it.size.toDouble()
        }
    }

    fun registerBusyConnectionsGauge(tags: Tags, supplier: () -> Int): Gauge =
        Gauge.builder("connectionpool.busyConnections", supplier).tags(tags).register(registry)

    fun registerPendingConnectionsGauge(tags: Tags, supplier: () -> Int): Gauge =
        Gauge.builder("connectionpool.pendingConnections", supplier).tags(tags).register(registry)

    fun registerAvailableConnectionsGauge(tags: Tags, supplier: () -> Int): Gauge =
        Gauge.builder("connectionpool.availableConnections", supplier).tags(tags).register(registry)

    fun registerConnectionAttemptsGauge(tags: Tags, supplier: () -> Int): Gauge =
        Gauge.builder("connectionpool.connectionAttempts", supplier).tags(tags).register(registry)

    fun registerConnectionFailuresGauge(tags: Tags, supplier: () -> Int): Gauge =
        Gauge.builder("connectionpool.connectionFailures", supplier).tags(tags).register(registry)

    fun registerConnectionsClosedGauge(tags: Tags, supplier: () -> Int): Gauge =
        Gauge.builder("connectionpool.connectionsClosed", supplier).tags(tags).register(registry)

    fun registerConnectionsTerminatedGauge(tags: Tags, supplier: () -> Int): Gauge =
        Gauge.builder("connectionpool.connectionsTerminated", supplier).tags(tags).register(registry)

    fun registerConnectionsInEstablishmentGauge(tags: Tags, supplier: () -> Int): Gauge =
        Gauge.builder("connectionpool.connectionsInEstablishment", supplier).tags(tags).register(registry)

    fun countRequestCancellation(cause: String) {
        registry.counter("proxy.request.cancelled", "cause", cause).increment()
    }

    fun <T> registerNettyAllocatorMemoryGauge(
        allocator: String,
        memoryType: String,
        stateObject: T,
        supplier: (T) -> Long
    ) {
        val tags = listOf(
            Tag.of("allocator", allocator),
            Tag.of("memoryType", memoryType)
        )

        Gauge.builder("netty.allocator.memory", stateObject) { supplier(stateObject).toDouble() }
            .tags(tags)
            .register(registry);
    }

    val responseStatus: StyxMetric = RealMetric("response_status")

    inner class RealMetric(private val name: String) : StyxMetric {
        override fun incrementCounter(statusTags: Tags) {
            registry.counter(name, statusTags).increment()
        }
    }
}


interface StyxMetric {
    fun incrementCounter(statusTags: Tags)
}

/* DELETE WHEN DONE
*
* # METRICS IN USE ON DATADOG PAGES

## On StyxPres Errors

(for status codes)
styx.response_status_total
styx.plugin_response_total

(for exception counts)
styx.styx_exception_total
styx.plugin_exception_total

(Also some built-in stuff)

## On StyxPres Connections
styx.proxy_request_cancelled (change the rest to tag)

styx.connectionpool_busyConnections
styx.connectionpool_pendingConnections
styx.connectionpool_availableConnections
styx.connectionpool_connectionAttempts
styx.connectionpool_connectionsClosed
styx.connectionpool_connectionsTerminated

## On StyxPres Uptime
(All builtin. also i think i dont have permission to edit)

## On StyxPres Requests
styx.proxy_request_received_total
styx.proxy_request_outstanding
styx.request_latency_seconds
styx.request_cancellation_total
styx.proxy_request_cancelled (change the rest to tag)

## On StyxPres response status codes
styx.response_status_total
styx.request_error_total

## StyxPres at a glance
styx.styx_error_total
styx.styx_response_total
styx.origins_response_status_count_total

## StyxPres k8s pod usage
(builtin)

## StyxPres DispatchApp
(separate application)

## StyxPres JVM Metrics
(bunch of builtin)

styx.jvm_netty_pooledAllocator_usedDirectMemory















.

*
* */
