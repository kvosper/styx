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

import com.hotels.styx.api.metrics.MeterFactory
import io.micrometer.core.instrument.*

class CentralisedMetrics(val registry: MeterRegistry) {
    /* How about:
         * a metric for responses. includes status, and anything else etc.
            * cannot combine latency because it is a different metric type - timer instead of counter
         * separate metrics for origin behaviours, designed not to get confused
     */

    // TODO should we use this syntax for the benefit of java code? probably not worth cluttering this class TBH
    //@get:JvmName("requestLatency")
    val requestLatency: StyxTimer = CmTimer("proxy.request.latency")

    val outstandingRequests: StyxGauge = CmGauge("proxy.request.outstanding")

    val busyConnections: StyxGauge = CmGauge("connectionpool.busyConnections")
    val pendingConnections: StyxGauge = CmGauge("connectionpool.pendingConnections")
    val availableConnections: StyxGauge = CmGauge("connectionpool.availableConnections")
    val connectionAttempts: StyxGauge = CmGauge("connectionpool.connectionAttempts")
    val connectionFailures: StyxGauge = CmGauge("connectionpool.connectionFailures")
    val connectionsClosed: StyxGauge = CmGauge("connectionpool.connectionsClosed")
    val connectionsTerminated: StyxGauge = CmGauge("connectionpool.connectionsTerminated")
    val connectionsInEstablishment: StyxGauge = CmGauge("connectionpool.connectionsInEstablishment")

    // We can preregister this easily because there are no tags used
    val styxErrors: StyxCounter = CmCounter("styx.error").preregister()

    private val responses: StyxCounter = CmCounter("proxy.response")
    private val requestsReceived: StyxCounter = CmCounter("proxy.request.received")

    private val requestCancellation: StyxCounter = CmCounter("proxy.request.cancelled")

    private val backendFaults: StyxCounter = CmCounter("backend.fault")

    fun countResponse(code: Int) {
        val tags: Tags = if (code in 100..599) {
            Tags.of("statusClass", (code / 100).toString() + "xx")
                .and("statusCode", code.toString())
        } else {
            Tags.of("statusClass", "unrecognised")
                .and("statusCode", "unrecognised")
        }

        responses.increment(tags)
    }

    fun countRequestReceived() = requestsReceived.increment()

    fun countRequestCancellation(cause: String) = requestCancellation.increment(Tags.of("cause", cause))

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

    fun countBackendFault(applicationId: String, type: String) = countBackendFault(applicationId, null, type)

    fun countBackendFault(applicationId: String, originId: String?, faultType: String) {
        val originTag = originId?.let { Tags.of("origin", originId) } ?: Tags.empty()

        val tags = Tags.of("application", applicationId).and("faultType", faultType).and(originTag)

        backendFaults.increment(tags)
    }

    inner class CmGauge(val name: String) : StyxGauge {
        override fun register(tags: Tags, supplier: () -> Int) =
            CmDeleter(Gauge.builder(name, supplier).tags(tags).register(registry))

        override fun register(supplier: () -> Int) =
            CmDeleter(Gauge.builder(name, supplier).register(registry))

        override fun <T> register(stateObject: T, function: (T) -> Number) {
            registry.gauge(name, stateObject) {
                function(it).toDouble()
            }
        }

        inner class CmDeleter(val gauge: Gauge) : StyxGauge.Deleter {
            override fun delete() {
                registry.remove(gauge)
            }
        }
    }

    inner class CmCounter(val name: String) : StyxCounter {
        override fun increment(statusTags: Tags) = registry.counter(name, statusTags).increment()

        override fun increment() = registry.counter(name).increment()

        override fun preregister(statusTags: Tags) = apply { registry.counter(name, statusTags) }

        override fun preregister() = apply { registry.counter(name) }
    }

    inner class CmTimer(val name: String) : StyxTimer {
        private val timer = MeterFactory.timer(registry, name)

        override fun startTiming() = CmStopper(Timer.start(registry))

        inner class CmStopper(private val startTime: Timer.Sample) : StyxTimer.Stopper {
            override fun stop() {
                startTime.stop(timer)
            }
        }
    }
}

interface StyxCounter {
    fun increment(statusTags: Tags)
    fun increment()

    // Prometheus requires that all meters with the same name have the same set of tag keys.
    fun preregister(statusTags: Tags): StyxCounter
    fun preregister(): StyxCounter
}

interface StyxGauge {
    fun register(tags: Tags, supplier: () -> Int): Deleter
    fun register(supplier: () -> Int): Deleter

    fun <T> register(stateObject: T, function: (T) -> Number)

    interface Deleter {
        fun delete()
    }
}

interface StyxTimer {
    fun startTiming(): Stopper

    interface Stopper {
        fun stop()
    }
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
