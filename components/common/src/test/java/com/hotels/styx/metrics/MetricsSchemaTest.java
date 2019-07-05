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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static ch.qos.logback.classic.Level.ERROR;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.COUNTER;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.HISTOGRAM;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.TIMER;
import static com.hotels.styx.metrics.MetricsSchema.metric;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class MetricsSchemaTest {
    private MetricsSchema schema;
    private CodaHaleMetricRegistry registry;
    private MetricRegistry validatedRegistry;
    private MetricRegistry validatedRegistryNoException;
    private LoggingTestSupport log;

    @BeforeMethod
    public void setUp() {
        log = new LoggingTestSupport(MetricsSchema.class);
        registry = new CodaHaleMetricRegistry();

        schema = new MetricsSchema(asList(
                metric("foo", COUNTER),
                metric("bar", HISTOGRAM),
                metric("wild.*", TIMER)
        ), true);

        validatedRegistry = schema.validated(registry);


        MetricsSchema schemaNoException = new MetricsSchema(asList(
                metric("foo", COUNTER),
                metric("bar", HISTOGRAM),
                metric("wild.*", TIMER)
        ), false);

        validatedRegistryNoException= schemaNoException.validated(registry);
    }

    @AfterMethod
    public void tearDown() {
        log.stop();
    }

    @Test
    public void allowsSpecifiedMetrics() {
        assertThat(validatedRegistry.counter("foo"), is(instanceOf(Counter.class)));
        assertThat(validatedRegistry.histogram("bar"), is(instanceOf(Histogram.class)));
        assertThat(validatedRegistry.timer("wild.card"), is(instanceOf(Timer.class)));
    }

    @Test(expectedExceptions = MetricNameNotInSchemaException.class, expectedExceptionsMessageRegExp = "Attempted to access COUNTER garble, but no metric with that name exists")
    public void disallowsUnspecifiedMetrics() {
        validatedRegistry.counter("garble");
    }

    @Test(expectedExceptions = MetricNameNotInSchemaException.class, expectedExceptionsMessageRegExp = "Attempted to access COUNTER bar, but metric bar is a HISTOGRAM")
    public void disallowsMetricsIfTypeDoesNotMatchSchema() {
        validatedRegistry.counter("bar");
    }

    @Test
    public void disallowsUnspecifiedMetricsNoException() {
        validatedRegistryNoException.counter("garble");
        assertThat(log.log(), hasItem(loggingEvent(ERROR, "Attempted to access COUNTER garble, but no metric with that name exists")));
    }

    @Test
    public void disallowsMetricsIfTypeDoesNotMatchSchemaNoException() {
        validatedRegistryNoException.counter("bar");
        assertThat(log.log(), hasItem(loggingEvent(ERROR, "Attempted to access COUNTER bar, but metric bar is a HISTOGRAM")));
    }

    @Test
    public void preregistersNonWildCardMetrics() {
        schema.preregister(registry);

        assertThat(registry.getCounters().get("foo"), is(notNullValue()));
        assertThat(registry.getHistograms().get("bar"), is(notNullValue()));

        assertThat(registry.getCounters().get("foo").getCount(), is(0L));
        assertThat(registry.getHistograms().get("bar").getCount(), is(0L));

        assertThat(registry.getTimers().get("wild.*"), is(nullValue()));
    }
}