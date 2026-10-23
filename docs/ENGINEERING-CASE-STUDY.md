# Honest acceptance in an asynchronous payment pipeline

## Problem and correction

The original ingestion method registered an idempotency key, called `KafkaTemplate.send`, and immediately returned `ACCEPTED`. The send method returns a future. A broker failure after that return could therefore leave the caller believing an event had been accepted when publication had failed.

The revised method waits for the future to complete before returning `ACCEPTED`. A failed or unconfirmed publication returns HTTP 503. Producer acknowledgements are `all`, and producer idempotence is enabled. This is an acknowledgement by the configured Kafka cluster, not proof of settlement. A single-broker development cluster provides no replica-failure tolerance.

`PaymentIngestionServiceTest.doesNotAcceptUntilBrokerAcknowledges` controls an incomplete future and verifies that the response cannot complete early. Other tests exercise synchronous failure, asynchronous failure, timeout, interruption, dedupe-store failure, and retry after an unknown outcome. Embedded Kafka tests cover actual broker publication, and the processing test verifies four failed handler attempts followed by a DLQ record.

## Tradeoff: latency buys a meaningful contract

Waiting for acknowledgement adds broker/network time to HTTP latency. `max.block.ms=5000` limits producer metadata/buffer blocking; the subsequent future wait defaults to 5000 ms. These are separate stages, so the complete request is not guaranteed to finish within five seconds. Dedupe-store connection waits are another latency source. The benchmark measures this acknowledged boundary explicitly.

## Remaining atomicity gap

Registering a request ID and publishing an event are two separate writes. A crash between them can leave a reserved ID with no event. Conversely, a lost acknowledgement can leave an event in Kafka with an HTTP 503. Deleting a reservation automatically after timeout would allow a retry to publish a second event. This implementation retains the reservation and reports the outcome as unconfirmed.

`DUPLICATE` means the ID exists; it does not prove the first attempt succeeded. The store does not compare payload hashes, so reusing an ID with different content is also reported as duplicate. Redis keys expire after 24 hours and are subject to Redis persistence/eviction configuration. SQL keys have no expiry policy. There is no implemented lookup/reconciliation endpoint, automated recovery of stranded reservations, or idempotent settlement ledger.

The next architectural change would persist a request state and outbox event atomically in PostgreSQL, relay the outbox with retries, and make the consumer's business mutation idempotent. Test crashes before and after commit, relay replay, and consumer replay against a real database. An outbox alone does not make external payment side effects exactly once.

## Reusable Java platform component

`platform-observability` provides a small `OperationMetrics` API shared by ingestion and processing. Operation and outcome are enums so request IDs and account IDs cannot become metric labels. The module records count, elapsed time, and histogram buckets. Meter registration is reusable per operation. Tests verify shared meter identity and outcome counts.

This is intentionally a modest library, not a claim to have built a complete observability platform. Current processing metrics represent the demonstration handler; they do not measure ledger commits or Kafka consumer lag.

## Interview walkthrough

1. Show the asynchronous future bug and the regression test.
2. Explain what an HTTP acceptance guarantees and how the guarantee affects latency.
3. Demonstrate 32 concurrent SQL reservations producing exactly one winner.
4. Show the retry count and DLQ assertions using an embedded broker.
5. Open the raw evidence, state the environment and measurement boundary, and explain why it cannot establish production capacity.
6. Explain the dual-write gap and sketch the outbox/consumer-ledger design as remaining work.

Relevant engineering signals are Java concurrency, API contracts, failure semantics, reusable instrumentation, and reproducible measurements. No employer endorsement or production deployment is implied.
