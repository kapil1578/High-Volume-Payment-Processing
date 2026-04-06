package com.hvpps.dashboard.core.model;

import java.util.List;

public record TransactionDetail(
        String transactionId,
        String type,
        double amount,
        String currency,
        String source,
        String destination,
        String status,
        int latencyMs,
        String channel,
        String riskScore,
        String traceId,
        String processorRef,
        String createdAt,
        String settledAt,
        String failureReason,
        List<TransactionTimelineEvent> timeline
) {
}
