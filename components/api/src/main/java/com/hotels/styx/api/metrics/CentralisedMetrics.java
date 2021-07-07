package com.hotels.styx.api.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;

import java.util.stream.Stream;

import static com.hotels.styx.api.metrics.CentralisedMetrics.MetricType.COUNTER;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A centralised specification of what metrics exist in Styx, also takes responsibility for publishing the metrics
 * so that the contract is not accidentally broken.
 */
public class CentralisedMetrics {
    private static final Logger LOGGER = getLogger(CentralisedMetrics.class);

    private final MeterRegistry meterRegistry;

    public CentralisedMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = requireNonNull(meterRegistry);

        preregisterMetrics();
    }

    // Anything that cannot be predicted ahead of time should be a metric tag not part of the metric name itself.
    private void preregisterMetrics() {
        Stream.of(MetricKey.values()).forEach(key -> {
            // todo is this the way to register with a count of 0?
            meterRegistry.counter(key.metricName()).increment(0);
        });
    }


    /**
     * Increment the count of a metric by 1.
     * <p>
     * If the metric is not of a suitable type, it will log an error.
     */
    public void mark(MetricKey metricKey, String... tags) {
        if (metricKey.type() == COUNTER) {
            meterRegistry.counter(metricKey.metricName(), tags).increment();

            // todo more types?
        } else {
            LOGGER.error("Tried to increment metric " + metricKey.metricName() + ", but this metric is a " + metricKey.type());
        }
    }


    public enum MetricKey {
        ;

        public String metricName() {
            throw new UnsupportedOperationException();
        }

        public MetricType type() {
            throw new UnsupportedOperationException();
        }
    }

    public enum MetricType {
        COUNTER
    }
}
