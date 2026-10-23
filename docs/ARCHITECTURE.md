# Architecture and guarantee boundaries

## Core path

1. Spring MVC validates `PaymentRequest` before entering ingestion.
2. `IdempotencyStore.registerIfAbsent` reserves the request ID using a SQL primary key or Redis SETNX with a 24-hour TTL.
3. An existing ID returns HTTP 202 with status `DUPLICATE`; this is a reservation result only.
4. A new ID is used as the Kafka record key. Ingestion waits for the producer future with the configured timeout.
5. Confirmed publication returns HTTP 202 with status `ACCEPTED`. Exceptions return HTTP 503, preserving interruption when applicable.
6. The demonstration consumer handles the event. Merchant ID `FAIL` triggers a deliberate failure, three retries, and forwarding to the same partition of `payments.v1.dlq`. DLQ send failures cause recovery to fail rather than silently succeed.

Keys partition by request ID, not account ID. No per-account ordering, account balances, settlement ledger, or payment-provider integration is implemented. Producer idempotence does not imply end-to-end exactly-once effects.

## Failure matrix

| Event | Caller sees | Persisted state / limitation |
| --- | --- | --- |
| Invalid request | 400 | No reservation or publish |
| Known reservation | 202 / DUPLICATE | No second send; original outcome may be unknown |
| Store unavailable | 503 | No Kafka send; store-side outcome itself may be unknown |
| Broker acknowledges | 202 / ACCEPTED | Event acknowledged, not settled |
| Producer error / timeout | 503 | Reservation retained; publication may or may not have happened |
| Crash after reservation before send | Connection failure | Reservation may be stranded |
| Handler throws | Ingestion response unchanged | Three retries, then DLQ |
| DLQ publish fails | No synchronous caller notification | Recovery throws; further handling follows Spring Kafka error handler |

See [case study](ENGINEERING-CASE-STUDY.md) for the outbox/consumer-ledger work needed to close the dual-write gap.

## Observability

`platform-observability` records `hvpps.operation` timers with operation (`ingestion`, `processing`) and outcome (`success`, `duplicate`, `failure`). Prometheus renders timer count/sum/buckets as `hvpps_operation_seconds_*`. Labels never include request or account identifiers. Ingestion latency spans reservation through broker ack. Processing latency spans one demo handler attempt, so retries each increment the failure count.

HTTP/JVM metrics are also exposed. No end-to-end settlement timer or consumer-lag collector is supplied. A dependency health check cannot validate payment consistency.

## Supporting components

- `api-gateway`: routes ingestion/dashboard requests and requires a real configured JWT issuer.
- `auth-service`: mock login/OAuth/OTP flows, not a production identity system.
- `dashboard-service`: synthetic transaction/partition data and synthetic SSE telemetry.
- `ui-dashboard`: React/Vite presentation of that synthetic data.
- `k8s`: deployment examples; not validated production infrastructure.

## Validation boundaries

The embedded tests exercise real HTTP and Kafka with H2. Store-level unit tests mock Redis; no real Redis failover is demonstrated. Context restart is tested, not process/power-loss recovery. The CI matrix targets Java 17 and 21; local results name the JDK actually used. The [evidence report](EVIDENCE.md) is the authority for what has run.
