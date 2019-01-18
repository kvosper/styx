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

import java.util.List;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

/**
 * A whitelist of metrics.
 */
public class MetricWhitelist {
    private final List<Pattern> whitelist;

    public MetricWhitelist(List<String> whitelist) {
        this.whitelist = whitelist.stream().map(Pattern::compile).collect(toList());
    }

    public boolean isPermittedMetric(String name) {
        // TODO find optimal implementation

        return whitelist.stream().anyMatch(pattern ->
                pattern.matcher(name).matches());
    }
}
