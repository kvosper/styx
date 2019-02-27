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
package com.hotels.styx.startup;

import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.AbstractService;
import com.hotels.styx.server.HttpServer;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A service that creates AND starts an HTTP server when started.
 */
public class ServerService extends AbstractService {
    private final Supplier<HttpServer> serverSupplier;

    public ServerService(Supplier<HttpServer> serverSupplier) {
        this.serverSupplier = memoize(serverSupplier);
    }

    private static <T> Supplier<T> memoize(Supplier<T> supplier) {
        requireNonNull(supplier);
        // convert been Java 8+ and Guava and back again
        return Suppliers.memoize(supplier::get)::get;
    }

    @Override
    protected void doStart() {
        HttpServer server = serverSupplier.get();
        server.startAsync().awaitRunning();
        notifyStarted();
    }

    @Override
    protected void doStop() {
        HttpServer server = serverSupplier.get();
        server.stopAsync().awaitTerminated();
        notifyStopped();
    }
}
