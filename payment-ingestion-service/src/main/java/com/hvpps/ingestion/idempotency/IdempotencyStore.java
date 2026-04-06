package com.hvpps.ingestion.idempotency;

public interface IdempotencyStore {
    boolean registerIfAbsent(String requestId);
}
