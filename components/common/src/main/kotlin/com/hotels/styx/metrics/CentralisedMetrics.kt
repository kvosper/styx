package com.hotels.styx.metrics

import com.hotels.styx.api.metrics.MeterFactory
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer

/*
 * TODO don't do too much of this work until we have an actual structure of which metrics we need/want.
 */
class CentralisedMetrics(val registry: MeterRegistry) {
    /*
    * TODO if we want to be able to tag some of these, perhaps the classes should not contain a single metric until done, but rather
    *  just a name that can have tags attached or something??
    * */
    val requestsOutstanding: GaugeAssigner = GaugeCreator("")
    val requestLatency : CmTimer = TimerThing("")
    val requestsReceived : Counter = registry.counter("")
    val responsesSent : Counter = registry.counter("")
    val requestsCancelled : CounterGroup = CG("")

    private inner class GaugeCreator(private val metricName: String) : GaugeAssigner {
        override fun <T> assignGauge(stateObject: T, valueFunction: (T) -> Double) {
            registry.gauge(metricName, stateObject, valueFunction);
        }
    }

    private inner class TimerThing(private val metricName: String) : CmTimer {
        val timer: Timer = MeterFactory.timer(registry, metricName)

        override fun start(): Stopper = StopperThing(Timer.start(registry))

        private inner class StopperThing(private val sample : Timer.Sample) : Stopper {
            override fun stop() {
                sample.stop(timer)
            }
        }
    }

    private inner class CG(private val metricName : String) : CounterGroup {
        override fun increment(vararg tags: String) {
            TODO("Not yet implemented")
        }

    }
}

interface CounterGroup {
    fun increment(vararg tags : String)
}

interface Stopper {
    fun stop()
}

interface CmTimer {
    fun start(): Stopper
}

interface GaugeAssigner {
    fun <T> assignGauge(stateObject: T, valueFunction: (T) -> Double)
}
