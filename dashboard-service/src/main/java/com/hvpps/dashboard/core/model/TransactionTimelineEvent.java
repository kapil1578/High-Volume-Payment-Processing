package com.hvpps.dashboard.core.model;

public record TransactionTimelineEvent(
        String timestamp,
        String status,
        String description
) {
}
