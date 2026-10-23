package com.hvpps.platform;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class OperationMetricsTest {
    @Test void aggregatesAcrossInstancesWithoutCreatingRequestSpecificMeters() {
        var registry = new SimpleMeterRegistry();
        var first = new OperationMetrics(registry, OperationMetrics.Operation.INGESTION);
        var second = new OperationMetrics(registry, OperationMetrics.Operation.INGESTION);
        first.record(OperationMetrics.Outcome.SUCCESS, System.nanoTime());
        second.record(OperationMetrics.Outcome.SUCCESS, System.nanoTime());
        second.record(OperationMetrics.Outcome.FAILURE, System.nanoTime());
        assertThat(registry.getMeters()).hasSize(3);
        assertThat(registry.get("hvpps.operation").tag("outcome", "success").timer().count()).isEqualTo(2);
        assertThat(registry.get("hvpps.operation").tag("outcome", "failure").timer().count()).isEqualTo(1);
    }
}
