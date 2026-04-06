package com.hvpps.dashboard.core.model;

public record FailedTxn(
        String transactionId,
        String reason,
        String failedAt
) {
}
