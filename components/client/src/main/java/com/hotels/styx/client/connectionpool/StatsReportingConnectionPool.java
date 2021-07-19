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
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.ConnectionPoolSettings;
import com.hotels.styx.client.Connection;
import com.hotels.styx.metrics.CentralisedMetrics;
import com.hotels.styx.metrics.StyxGauge;
import io.micrometer.core.instrument.Tags;
import org.reactivestreams.Publisher;

import java.util.HashSet;
import java.util.Set;

import static com.hotels.styx.api.Metrics.APPID_TAG;
import static com.hotels.styx.api.Metrics.ORIGINID_TAG;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

class StatsReportingConnectionPool implements ConnectionPool {
    private final ConnectionPool connectionPool;
    private final Set<StyxGauge.Deleter> deleters = new HashSet<>();
    private final Tags tags;
    private final CentralisedMetrics metrics;

    public StatsReportingConnectionPool(ConnectionPool connectionPool, CentralisedMetrics metrics) {
        this.connectionPool = requireNonNull(connectionPool);
        this.metrics = metrics;
        this.tags = Tags.of(APPID_TAG, connectionPool.getOrigin().applicationId().toString(),
                ORIGINID_TAG, connectionPool.getOrigin().id().toString());
        registerMetrics();
    }

    @Override
    public Origin getOrigin() {
        return connectionPool.getOrigin();
    }

    @Override
    public Publisher<Connection> borrowConnection() {
        return connectionPool.borrowConnection();
    }

    @Override
    public boolean returnConnection(Connection connection) {
        return connectionPool.returnConnection(connection);
    }

    @Override
    public boolean closeConnection(Connection connection) {
        return connectionPool.closeConnection(connection);
    }

    @Override
    public boolean isExhausted() {
        return connectionPool.isExhausted();
    }

    @Override
    public Stats stats() {
        return connectionPool.stats();
    }

    @Override
    public ConnectionPoolSettings settings() {
        return connectionPool.settings();
    }

    @Override
    public void close() {
        connectionPool.close();
        removeMetrics();
    }

    private void registerMetrics() {
        ConnectionPool.Stats stats = connectionPool.stats();

        deleters.addAll(asList(
                metrics.getBusyConnections().register(tags, stats::busyConnectionCount),
                metrics.getPendingConnections().register(tags, stats::pendingConnectionCount),
                metrics.getAvailableConnections().register(tags, stats::availableConnectionCount),
                metrics.getConnectionAttempts().register(tags, stats::connectionAttempts),
                metrics.getConnectionFailures().register(tags, stats::connectionFailures),
                metrics.getConnectionsClosed().register(tags, stats::closedConnections),
                metrics.getConnectionsTerminated().register(tags, stats::terminatedConnections),
                metrics.getConnectionsInEstablishment().register(tags, stats::connectionsInEstablishment)
        ));
    }

    private void removeMetrics() {
        deleters.forEach(StyxGauge.Deleter::delete);
        deleters.clear();
    }
}
