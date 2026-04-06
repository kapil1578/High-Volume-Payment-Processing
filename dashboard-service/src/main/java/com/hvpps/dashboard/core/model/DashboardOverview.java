package com.hvpps.dashboard.core.model;

public record DashboardOverview(
        int throughputPerSecond,
        double successRatePercent,
        int avgLatencyMs,
        int failedTransactions
) {
}
