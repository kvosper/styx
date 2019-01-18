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
package com.hotels.styx.api.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;
import com.hotels.styx.api.MetricRegistry;

import java.util.SortedMap;
import java.util.SortedSet;

import static java.util.Objects.requireNonNull;

/**
 * A metric registry that only allows metrics to be registered if they are on a whitelist.
 *
 * This allows us to have one centralised "source-of-truth" to list all of the metrics.
 */
public class WhitelistMetricRegistry implements MetricRegistry {
    private final MetricRegistry delegate;
    private final MetricWhitelist whitelist;

    public WhitelistMetricRegistry(MetricRegistry delegate, MetricWhitelist whitelist) {
        this.delegate = requireNonNull(delegate);
        this.whitelist = requireNonNull(whitelist);
    }

    private void rejectMetricIfNotWhitelisted(String name) {
        if (!whitelist.isPermittedMetric(name)) {
            throw new IllegalArgumentException("Metric '" + name + "' is not registered in the whitelist");
        }
    }

    @Override
    public MetricRegistry scope(String name) {
        return delegate.scope(name);
    }

    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        rejectMetricIfNotWhitelisted(name);

        return delegate.register(name, metric);
    }

    @Override
    public boolean deregister(String name) {
        return delegate.deregister(name);
    }

    @Override
    public Counter counter(String name) {
        rejectMetricIfNotWhitelisted(name);

        return delegate.counter(name);
    }

    @Override
    public Histogram histogram(String name) {
        rejectMetricIfNotWhitelisted(name);

        return delegate.histogram(name);
    }

    @Override
    public Meter meter(String name) {
        rejectMetricIfNotWhitelisted(name);

        return delegate.meter(name);
    }

    @Override
    public Timer timer(String name) {
        rejectMetricIfNotWhitelisted(name);

        return delegate.timer(name);
    }

    @Override
    public void addListener(MetricRegistryListener listener) {
        delegate.addListener(listener);
    }

    @Override
    public void removeListener(MetricRegistryListener listener) {
        delegate.removeListener(listener);
    }

    @Override
    public SortedSet<String> getNames() {
        return delegate.getNames();
    }

    @Override
    public SortedMap<String, Gauge> getGauges() {
        return delegate.getGauges();
    }

    @Override
    public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
        return delegate.getGauges(filter);
    }

    @Override
    public SortedMap<String, Counter> getCounters() {
        return delegate.getCounters();
    }

    @Override
    public SortedMap<String, Counter> getCounters(MetricFilter filter) {
        return delegate.getCounters(filter);
    }

    @Override
    public SortedMap<String, Histogram> getHistograms() {
        return delegate.getHistograms();
    }

    @Override
    public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
        return delegate.getHistograms(filter);
    }

    @Override
    public SortedMap<String, Meter> getMeters() {
        return delegate.getMeters();
    }

    @Override
    public SortedMap<String, Meter> getMeters(MetricFilter filter) {
        return delegate.getMeters(filter);
    }

    @Override
    public SortedMap<String, Timer> getTimers() {
        return delegate.getTimers();
    }

    @Override
    public SortedMap<String, Timer> getTimers(MetricFilter filter) {
        return delegate.getTimers(filter);
    }

    @Override
    public SortedMap<String, Metric> getMetrics() {
        return delegate.getMetrics();
    }
}
