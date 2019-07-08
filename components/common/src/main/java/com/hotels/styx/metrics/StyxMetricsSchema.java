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
package com.hotels.styx.metrics;

import static com.hotels.styx.metrics.MetricsSchema.MetricType.COUNTER;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.GAUGE;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.HISTOGRAM;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.METER;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.TIMER;
import static com.hotels.styx.metrics.MetricsSchema.metric;
import static java.util.Arrays.asList;

/**
 * Contains the schema for Styx metrics.
 */
public final class StyxMetricsSchema {
    // TODO use system property to determine if we should throw exceptions? (no prop = prod, prop = test?)
    public static final MetricsSchema STYX_METRICS_SCHEMA = new MetricsSchema(asList(
            metric("origins.*.*.connectionspool.busy-connections", GAUGE),
            metric("origins.*.*.connectionspool.pending-connections", GAUGE),
            metric("origins.*.*.connectionspool.available-connections", GAUGE),
            metric("origins.*.*.connectionspool.connection-attempts", GAUGE),
            metric("origins.*.*.connectionspool.connection-failures", GAUGE),
            metric("origins.*.*.connectionspool.connections-closed", GAUGE),
            metric("origins.*.*.connectionspool.connections-terminated", GAUGE),
            metric("origins.*.*.connectionspool.connections-in-establishment", GAUGE),
            metric("origins.*.*.status", GAUGE),
            metric("origins.*.healthcheck.failure", METER),
            metric("origins.*.requests.success-rate", METER),
            metric("origins.*.requests.error-rate", METER),
            metric("origins.*.requests.latency", TIMER),
            metric("origins.*.*.requests.success-rate", METER),
            metric("origins.*.*.requests.error-rate", METER),
            metric("origins.*.*.requests.latency", TIMER),

            metric("origins.*.requests.response.status.200", METER),
            metric("origins.*.requests.cancelled", COUNTER),

            metric("requests.success-rate", METER),
            metric("requests.error-rate.500", METER),
            metric("requests.latency", TIMER),

            metric("connections.bytes-received", COUNTER),
            metric("connections.bytes-sent", COUNTER),
            metric("connections.total-connections", COUNTER),
            metric("requests.outstanding", COUNTER),
            metric("requests.received", METER),
            metric("requests.response.sent", COUNTER),

            metric("jvm.bufferpool", GAUGE),
            metric("jvm.memory", GAUGE),
            metric("jvm.thread", GAUGE),
            metric("jvm.gc", GAUGE),
            metric("jvm.uptime", GAUGE),
            metric("jvm.uptime.formatted", GAUGE),
            metric("jvm.netty", GAUGE),
            metric("jvm.netty", GAUGE),
            metric("os", GAUGE),

            metric("requests.response.status.unrecognised", COUNTER),
            metric("requests.response.status.1xx", COUNTER),
            metric("requests.response.status.2xx", COUNTER),
            metric("requests.response.status.3xx", COUNTER),
            metric("requests.response.status.4xx", COUNTER),
            metric("requests.response.status.5xx", COUNTER),
            metric("requests.response.status.501", COUNTER),
            metric("requests.response.status.502", COUNTER),
            metric("requests.response.status.503", COUNTER),
            metric("requests.response.status.504", COUNTER),
            metric("requests.response.status.521", COUNTER),

            metric("styx.response.status.502", COUNTER),
            metric("styx.exception.*", COUNTER),
            metric("connections.eventloop.*.registered-channel-count", COUNTER),
            metric("connections.eventloop.*.channels", HISTOGRAM),

            metric("styx.server.http.responses.502", METER),

            metric("requests.cancelled.responseError", COUNTER),

            metric("connections.idleClosed", HISTOGRAM),

            metric("styx.server.http.requests", METER),

            metric("requests.response.status.500", COUNTER)
    ), false);

    // We could have a main method that generates documentation?

    private StyxMetricsSchema() {
    }
}
