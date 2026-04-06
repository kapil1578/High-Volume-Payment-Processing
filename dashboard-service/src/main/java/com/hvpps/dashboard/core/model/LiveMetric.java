package com.hvpps.dashboard.core.model;

public record LiveMetric(
        int throughputPerSecond,
        int failedTransactions,
        int latencyMs
) {
}
