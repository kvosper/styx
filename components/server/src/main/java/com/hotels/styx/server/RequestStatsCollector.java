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
package com.hotels.styx.server;

import com.hotels.styx.metrics.CentralisedMetrics;
import com.hotels.styx.metrics.StyxTimer;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * An implementation of request event sink that maintains Styx request statistics.
 */
public class RequestStatsCollector implements RequestProgressListener {
    private final CentralisedMetrics metrics;
    private final StyxTimer latencyTimer;
    private final ConcurrentHashMap<Object, StyxTimer.Stopper> ongoingRequests = new ConcurrentHashMap<>();

    /**
     * Constructs a collector with a {@link MeterRegistry} to report stastistics to.
     *
     * @param metrics a registry to report to
     */
    public RequestStatsCollector(CentralisedMetrics metrics) {
        this.metrics = requireNonNull(metrics);
        metrics.getOutstandingRequests().register(ongoingRequests, ConcurrentHashMap::size);

        this.latencyTimer = metrics.getRequestLatency();
    }

    @Override
    public void onRequest(Object requestId) {
        StyxTimer.Stopper previous = this.ongoingRequests.putIfAbsent(requestId, latencyTimer.startTiming());
        if (previous == null) {
            metrics.countRequestReceived();
        }
    }

    @Override
    public void onComplete(Object requestId, int responseStatus) {
        StyxTimer.Stopper startTime = this.ongoingRequests.remove(requestId);
        if (startTime != null) {
            metrics.countResponse(responseStatus);

            startTime.stop();
        }
    }

    @Override
    public void onTerminate(Object requestId) {
        StyxTimer.Stopper startTime = this.ongoingRequests.remove(requestId);
        if (startTime != null) {
            startTime.stop();
        }
    }
}
