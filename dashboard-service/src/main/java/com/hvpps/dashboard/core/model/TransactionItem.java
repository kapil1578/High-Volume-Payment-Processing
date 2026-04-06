package com.hvpps.dashboard.core.model;

public record TransactionItem(
        String transactionId,
        String type,
        double amount,
        String source,
        String destination,
        String status,
        int latencyMs
) {
}
