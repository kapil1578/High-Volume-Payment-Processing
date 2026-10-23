# Portfolio presentation kit

## Suggested repository description

Java/Spring Boot + Kafka payment-ingestion lab: acknowledged API contracts, concurrent deduplication tests, retry/DLQ evidence, shared metrics and reproducible benchmarks.

## A three-minute demonstration

1. Open the README and explain the acceptance boundary in one sentence.
2. Show the delayed-ack regression test and why an unobserved future can produce a false success.
3. Run Maven verification and open the retry/DLQ and concurrent-reservation reports.
4. Open the saved benchmark JSON, state the workload/environment, and show p95/p99 alongside the raw samples.
5. End with the dual-write limitation and the next outbox/ledger design. Do not demonstrate the synthetic dashboard as live payment telemetry.

## Resume wording supported by implementation

Built a Java/Spring Boot payment-ingestion prototype with Kafka-acknowledged API responses, SQL/Redis request deduplication, retry/DLQ handling, a shared Micrometer instrumentation module, and automated concurrency/failure tests; added reproducible HTTP-to-Kafka benchmark tooling.

Only add test counts or performance numbers after checking the evidence report for the exact revision and environment. Do not call a short embedded-broker smoke result sustained production throughput.

## Shareable project note (draft)

I built a Java/Kafka ingestion project around a failure I could reproduce: returning ACCEPTED before an asynchronous broker send completes. I corrected the response contract and added regression tests for delayed acknowledgement, failure, timeout and concurrent duplicate requests. The repository includes raw local benchmark samples and a write-up of the remaining dual-write gap. I would welcome technical feedback on the outbox and idempotent-consumer design.

Add the repository link when sharing. This draft has not been posted or sent to anyone. Publishing this branch, pinning the repository and sharing the case study makes the work discoverable; no project can guarantee attention from a particular employer.
