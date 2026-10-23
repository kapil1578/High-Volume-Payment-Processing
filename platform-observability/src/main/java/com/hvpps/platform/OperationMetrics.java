package com.hvpps.platform;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Reusable bounded-cardinality telemetry. Never tag metrics with customer/request IDs. */
public final class OperationMetrics {
    public enum Operation { INGESTION, PROCESSING }
    public enum Outcome { SUCCESS, DUPLICATE, FAILURE }
    private final Map<Outcome, Timer> timers = new EnumMap<>(Outcome.class);

    public OperationMetrics(MeterRegistry registry, Operation operation) {
        for (Outcome outcome : Outcome.values()) {
            timers.put(outcome, Timer.builder("hvpps.operation")
                    .tag("operation", operation.name().toLowerCase(java.util.Locale.ROOT))
                    .tag("outcome", outcome.name().toLowerCase(java.util.Locale.ROOT))
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }

    public void record(Outcome outcome, long startedNanos) {
        timers.get(outcome).record(Math.max(0, System.nanoTime() - startedNanos), TimeUnit.NANOSECONDS);
    }
}
