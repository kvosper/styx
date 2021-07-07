package com.hotels.styx.api.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import static com.hotels.styx.api.metrics.OldCentralisedMetrics.MetricType.COUNTER;
import static com.hotels.styx.api.metrics.OldCentralisedMetrics.MetricType.GAUGE;
import static com.hotels.styx.api.metrics.OldCentralisedMetrics.MetricType.TIMER;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A centralised specification of what metrics exist in Styx, also takes responsibility for publishing the metrics
 * so that the contract is not accidentally broken.
 */
public class OldCentralisedMetrics {
    private static final Logger LOGGER = getLogger(OldCentralisedMetrics.class);

    private final MeterRegistry meterRegistry;
//    private final Map<MetricKey, Timer> timers

    public OldCentralisedMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = requireNonNull(meterRegistry);

        preregisterMetrics();
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

    public <T> void useAsGauge(MetricKey metricKey, T stateObject, ToDoubleFunction<T> function) {
        if (metricKey.type() == GAUGE) {
            meterRegistry.gauge(metricKey.metricName(), stateObject, function);
        } else {
            LOGGER.error("Tried to assign a gauge to " + metricKey.metricName() + ", but this metric is a " + metricKey.type());
        }
    }

    // Anything that cannot be predicted ahead of time should be a metric tag not part of the metric name itself.
    private void preregisterMetrics() {
        Stream.of(MetricKey.values()).forEach(key -> {
            if (key.type() == COUNTER) {
                meterRegistry.counter(key.metricName()).increment(0);
            } else if (key.type() == TIMER) {
                MeterFactory.timer(meterRegistry, key.metricName());
            }
        });
    }

    public enum MetricKey {
        // ARRANGE ALPHABETICALLY
        REQUESTS_CANCELLED,
        REQUESTS_OUTSTANDING,
        ;

        public String metricName() {
            throw new UnsupportedOperationException();
        }

        public MetricType type() {
            throw new UnsupportedOperationException();
        }
    }

    public enum MetricType {
        COUNTER, GAUGE, TIMER
    }

    private static final class MeterFactory {
        private static final Duration DEFAULT_MIN_HISTOGRAM_BUCKET = Duration.of(1, MILLIS);
        private static final Duration DEFAULT_MAX_HISTOGRAM_BUCKET = Duration.of(1, MINUTES);

        private static final String MIN_ENV_VAR_NAME = "STYX_TIMER_HISTO_MIN";
        private static final String MAX_ENV_VAR_NAME = "STYX_TIMER_HISTO_MIN";

        private static final Duration MIN_HISTOGRAM_BUCKET = of(MIN_ENV_VAR_NAME)
                .map(System::getenv)
                .map(Long::valueOf)
                .map(millis -> Duration.of(millis, MILLIS))
                .orElse(DEFAULT_MIN_HISTOGRAM_BUCKET);
        private static final Duration MAX_HISTOGRAM_BUCKET = of(MAX_ENV_VAR_NAME)
                .map(System::getenv)
                .map(Long::valueOf)
                .map(millis -> Duration.of(millis, MILLIS))
                .orElse(DEFAULT_MAX_HISTOGRAM_BUCKET);

        private MeterFactory() {
        }

        public static Timer timer(MeterRegistry registry, String name) {
            return timer(registry, name, Tags.empty());
        }

        public static Timer timer(MeterRegistry registry, String name, Iterable<Tag> tags) {
            return Timer.builder(name)
                    .tags(tags)
                    .publishPercentileHistogram()
                    .minimumExpectedValue(MIN_HISTOGRAM_BUCKET)
                    .maximumExpectedValue(MAX_HISTOGRAM_BUCKET)
                    .register(registry);
        }
    }

}
