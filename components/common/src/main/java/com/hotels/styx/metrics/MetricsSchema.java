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
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.ScopedMetricRegistry;
import org.slf4j.Logger;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.BiFunction;

import static com.hotels.styx.metrics.MetricsSchema.MetricType.COUNTER;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.GAUGE;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.HISTOGRAM;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.METER;
import static com.hotels.styx.metrics.MetricsSchema.MetricType.TIMER;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Schema to define which metrics we have in Styx.
 * This can be used to validate metrics when registering, allowing us to avoid adding undocumented metrics:
 * <p>
 * validatedRegistry = schema.validated(registry)
 * <p>
 * and also to preregister metrics with default (zero) values to help with visualisation software.
 * <p>
 * schema.preregister(registry)
 * <p>
 * Metrics can be defined specifically, e.g.
 * <p>
 * metric("foo.bar.baz", COUNTER)
 * <p>
 * Or with wildcards, e.g.
 * <p>
 * metric("foo.bar.*", COUNTER)
 * <p>
 * Note that a wildcard part must be an asterisk alone, so "foo.bar.b*" is not allowed.
 * <p>
 * It can be worthwhile to have overlapping wildcards and specifics if you want to preregister certain names within
 * a wildcard range.
 */
public class MetricsSchema {
    private static final Logger LOGGER = getLogger(MetricsSchema.class);

    private final boolean throwExceptionOnInvalidRegistration;
    private final SimpleTree<MetricSpecification> metrics;

    /**
     * Construct an instance.
     * <p>
     * You must provide a list of valid metrics and also say whether to throw an exception when someone
     * attempts to register a metric with an invalid name. If set to "false", we will just log a message
     * instead.
     * <p>
     * Recommend use: "true" for tests, "false" for production usage.
     *
     * @param metrics                             metrics list
     * @param throwExceptionOnInvalidRegistration should an exception be thrown
     */
    public MetricsSchema(List<MetricSpecification> metrics, boolean throwExceptionOnInvalidRegistration) {
        this.metrics = new SimpleTree<>();
        metrics.forEach(metric -> this.metrics.add(metric.name(), metric));
        this.throwExceptionOnInvalidRegistration = throwExceptionOnInvalidRegistration;
    }

    public static MetricSpecification metric(String name, MetricType type) {
        return new MetricSpecification(name, type);
    }

    // Ugly, but we need this for JmxReporterServiceFactory
    public static MetricRegistry unwrapValidatedMetricRegistry(MetricRegistry registry) {
        return registry instanceof ValidatingRegistry
                ? ((ValidatingRegistry) registry).registry
                : registry;
    }

    public MetricRegistry validated(MetricRegistry registry) {
        return new ValidatingRegistry(registry);
    }

    // Preregisters all non-wildcard metrics in schema, except gauges (because those require a specific callback).
    public void preregister(MetricRegistry registry) {
        metrics.walkNonWildcards((name, spec) ->
                spec.metricType.preregisterIsPossible(registry, name));
    }

    private void failIfInvalid(String name, MetricType type) {
        MetricSpecification spec = metrics.valueOf(name).orElse(null);

        String errorMessage = null;

        if (spec == null) {
            errorMessage = format("Invalid metric: %s %s, does not exist in schema", type, name);
        } else if (spec.metricType() != type) {
            errorMessage = format("Invalid metric: %s %s, schema declares %s as a %s", type, name, name, spec.metricType());
        }

        if (errorMessage != null) {
            if (throwExceptionOnInvalidRegistration) {
                throw new MetricNameNotInSchemaException(errorMessage);
            }

            LOGGER.error(errorMessage);
        }
    }

    /**
     * Registry that checks if metrics are valid before allowing them to be used.
     */
    private class ValidatingRegistry implements MetricRegistry {
        private final MetricRegistry registry;

        private ValidatingRegistry(MetricRegistry registry) {
            this.registry = requireNonNull(registry);
        }

        @Override
        public MetricRegistry scope(String name) {
            return new ScopedMetricRegistry(name, this);
        }

        @Override
        public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
            failIfInvalid(name, GAUGE);
            return registry.register(name, metric);
        }

        @Override
        public boolean deregister(String name) {
            return registry.deregister(name);
        }

        @Override
        public Counter counter(String name) {
            failIfInvalid(name, COUNTER);
            return registry.counter(name);
        }

        @Override
        public Histogram histogram(String name) {
            failIfInvalid(name, HISTOGRAM);
            return registry.histogram(name);
        }

        @Override
        public Meter meter(String name) {
            failIfInvalid(name, METER);
            return registry.meter(name);
        }

        @Override
        public Timer timer(String name) {
            failIfInvalid(name, TIMER);
            return registry.timer(name);
        }

        @Override
        public void addListener(MetricRegistryListener listener) {
            registry.addListener(listener);
        }

        @Override
        public void removeListener(MetricRegistryListener listener) {
            registry.removeListener(listener);
        }

        @Override
        public SortedSet<String> getNames() {
            return registry.getNames();
        }

        @Override
        public SortedMap<String, Gauge> getGauges() {
            return registry.getGauges();
        }

        @Override
        public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
            return registry.getGauges(filter);
        }

        @Override
        public SortedMap<String, Counter> getCounters() {
            return registry.getCounters();
        }

        @Override
        public SortedMap<String, Counter> getCounters(MetricFilter filter) {
            return registry.getCounters(filter);
        }

        @Override
        public SortedMap<String, Histogram> getHistograms() {
            return registry.getHistograms();
        }

        @Override
        public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
            return registry.getHistograms(filter);
        }

        @Override
        public SortedMap<String, Meter> getMeters() {
            return registry.getMeters();
        }

        @Override
        public SortedMap<String, Meter> getMeters(MetricFilter filter) {
            return registry.getMeters(filter);
        }

        @Override
        public SortedMap<String, Timer> getTimers() {
            return registry.getTimers();
        }

        @Override
        public SortedMap<String, Timer> getTimers(MetricFilter filter) {
            return registry.getTimers(filter);
        }

        @Override
        public SortedMap<String, Metric> getMetrics() {
            return registry.getMetrics();
        }
    }

    /**
     * Specification of a single metric we expect to be created.
     */
    public static class MetricSpecification {
        private final String name;
        private final MetricType metricType;

        private MetricSpecification(String name, MetricType metricType) {
            this.name = name;
            this.metricType = metricType;
        }

        public String name() {
            return name;
        }

        public MetricType metricType() {
            return metricType;
        }
    }

    /**
     * Types of metrics we support.
     */
    public enum MetricType {
        COUNTER(MetricRegistry::counter),
        METER(MetricRegistry::meter),
        TIMER(MetricRegistry::timer),
        HISTOGRAM(MetricRegistry::histogram),
        GAUGE(null);

        private final BiFunction<MetricRegistry, String, Metric> registryFunction;

        MetricType(BiFunction<MetricRegistry, String, Metric> registryFunction) {
            this.registryFunction = registryFunction;
        }

        public void preregisterIsPossible(MetricRegistry registry, String name) {
            if (registryFunction != null) {
                registryFunction.apply(registry, name);
            }
        }
    }
}
