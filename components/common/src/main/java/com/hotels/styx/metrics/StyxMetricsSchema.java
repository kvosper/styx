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

import static com.hotels.styx.metrics.MetricsSchema.metric;
import static java.util.Arrays.asList;

/**
 * Contains the schema for Styx metrics.
 */
public final class StyxMetricsSchema {
    // TODO use system property to determine if we should throw exceptions? (no prop = prod, prop = test?)
    public static final MetricsSchema STYX_METRICS_SCHEMA = new MetricsSchema(asList(
            metric("requests.response.status.500", MetricsSchema.MetricType.COUNTER)
    ), false);

    // We could have a main method that generates documentation?

    private StyxMetricsSchema() {
    }
}
