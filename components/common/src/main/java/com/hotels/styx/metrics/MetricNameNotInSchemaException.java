package com.hotels.styx.metrics;

/**
 * An exception thrown when someone attempts to register a metric with an invalid name.
 */
public class MetricNameNotInSchemaException extends RuntimeException {
    public MetricNameNotInSchemaException() {
    }

    public MetricNameNotInSchemaException(String message) {
        super(message);
    }

    public MetricNameNotInSchemaException(String message, Throwable cause) {
        super(message, cause);
    }

    public MetricNameNotInSchemaException(Throwable cause) {
        super(cause);
    }
}
