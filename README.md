# High-Volume Payment Processing System

Java 17 + Spring Boot + Kafka + Kubernetes (GKE) starter for high-throughput payment ingestion and async processing.

## Complete documentation

- Architecture and end-to-end technical documentation:
  - `docs/ARCHITECTURE.md`

## Why this architecture works

- **Sync + Async split**: REST accepts client requests quickly, Kafka decouples ingestion from processing.
- **Throughput scaling**: topic partitions and consumer-group size are the primary TPS levers.
- **Security-first edge**: OAuth2/JWT validation at the gateway before business logic.
- **Operational visibility**: logs + metrics hooks are enabled via Actuator and structured logging.
- **Cloud-ready**: deployment model targets Google Cloud (`GKE`, `Cloud Monitoring`, `Cloud Logging`).

## System layout

- `payment-contracts`: shared request/event DTOs.
- `dashboard-service`: UI-facing dashboard APIs + live SSE metrics.
- `payment-ingestion-service`: REST API (`POST /api/payments`) + Kafka producer.
- `payment-processing-service`: Kafka consumer for async business processing.
- `ui-dashboard`: React/Vite operations dashboard UI.

## Added production capabilities

- **Persistent idempotency**
  - Redis-backed request deduplication is enabled by default.
  - Cloud SQL-compatible mode is available via JDBC (`HVPPS_IDEMPOTENCY_STORE=sql`).
  - SQL schema for idempotency key table lives at `payment-ingestion-service/src/main/resources/schema.sql`.
- **OpenAPI 3 + Swagger UI**
  - OpenAPI JSON: `http://localhost:8081/v3/api-docs`
  - Swagger UI: `http://localhost:8081/swagger-ui.html`
- **Dashboard API + live stream**
  - Overview: `GET /api/dashboard/overview`
  - Transactions: `GET /api/dashboard/transactions`
  - Kafka partitions: `GET /api/dashboard/kafka/partitions`
  - Failed txns: `GET /api/dashboard/failed`
  - Live updates: `GET /api/dashboard/stream` (SSE)
- **Retry + DLQ**
  - Processing consumer retries failed events 3 times (1 second backoff).
  - Events still failing are sent to `payments.v1.dlq`.
- **Performance load script**
  - k6 script at `performance/k6-payments-1000tps.js` targets 1,000 requests/second.

## Quick start

1. Start local dependencies (Kafka + Redis + Postgres):

```bash
docker compose up -d
```

2. Build all modules:

```bash
mvn clean package
```

3. Run services (separate terminals):

```bash
mvn -pl api-gateway spring-boot:run
mvn -pl dashboard-service spring-boot:run
mvn -pl payment-ingestion-service spring-boot:run
mvn -pl payment-processing-service spring-boot:run
```

4. Run dashboard UI:

```bash
cd ui-dashboard
npm install
npm run dev
```

5. Open dashboard:

```text
http://localhost:5173
```

6. Send a payment:

```bash
curl -X POST http://localhost:8081/api/payments \
  -H "Content-Type: application/json" \
  -d '{
    "requestId": "req-1001",
    "accountId": "acct-42",
    "amount": 250.75,
    "currency": "USD",
    "merchantId": "mrc-9001"
  }'
```

## GCP production target

- Container registry: `Artifact Registry`
- Cluster runtime: `Google Kubernetes Engine (GKE)`
- Kafka option: `Confluent Cloud` or self-managed Kafka on GKE
- Metrics/logging: `Cloud Monitoring` + `Cloud Logging`
- Secret management: `Secret Manager`
- Cloud SQL mode for idempotency store (PostgreSQL) is supported with JDBC config.

## Idempotency backend selection

- Default (`redis`):
  - `HVPPS_IDEMPOTENCY_STORE=redis`
  - `REDIS_HOST=localhost`
  - `REDIS_PORT=6379`
- Cloud SQL/JDBC (`sql`):
  - `HVPPS_IDEMPOTENCY_STORE=sql`
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/payments`
  - `SPRING_DATASOURCE_USERNAME=<user>`
  - `SPRING_DATASOURCE_PASSWORD=<password>`

## DLQ and failure handling

- To test retry + DLQ quickly, send a payment with `merchantId` set to `FAIL`.
- The processing service throws a simulated downstream exception and Kafka moves the event through retry to DLQ.

## k6 load test (1,000+ TPS)

Install k6 and run:

```bash
k6 run performance/k6-payments-1000tps.js
```

To target a different endpoint:

```bash
k6 run -e BASE_URL=http://localhost:8081 performance/k6-payments-1000tps.js
```

## Next steps

- Add contract/integration tests for retry and DLQ behavior.
- Externalize idempotency TTL and partition counts via centralized config.
- Add tracing (OpenTelemetry) and end-to-end correlation IDs.
