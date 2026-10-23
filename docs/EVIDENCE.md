# Executed evidence — 23 October 2026

## Clean Java verification

Executed locally with Maven 3.9.9 and Temurin JDK 21.0.11 on Windows 11 amd64:

```sh
mvn -B -ntp -Dportfolio.benchmark=true clean verify
```

**BUILD SUCCESS: 21 tests, 0 failures, 0 errors, 0 skipped.** All eight Maven reactor entries built successfully. This includes the opt-in measurement test. A normal `verify` skips that one measurement test. Hosted CI and Java 17 execution have not been observed; the supplied workflow configures both JDK 17 and 21.

| Test group | Cases | What was exercised |
| --- | ---: | --- |
| Ingestion service | 8 | Delayed ack, duplicate, asynchronous/synchronous failures, timeout, store failure, retry after unknown outcome, interruption |
| Request validation | 5 | Missing fields, zero amount, invalid currency length, blank request ID, malformed JSON |
| SQL concurrency | 1 | 32 attempts at one ID, exactly one winner, separate ID accepted |
| Redis adapter | 1 | Mocked new/duplicate/null results; no live Redis used |
| Context restart | 2 | Accepted request then persisted SQL reservation after Spring context restart |
| OpenAPI | 1 | Published API schema includes payment endpoint |
| Retry/DLQ | 1 | Real embedded Kafka; four failed attempts and matching DLQ event |
| Shared metrics | 1 | Reused meters, bounded outcomes and recorded counts |
| HTTP-to-Kafka smoke | 1 | 20 warmup + 200 measured requests; correct acknowledgement IDs |

### Coverage scope

JaCoCo line counts from this clean run:

| Module | Covered / total lines | Line coverage |
| --- | ---: | ---: |
| payment-ingestion-service | 55 / 57 | 96.5% |
| payment-processing-service | 19 / 24 | 79.2% |
| platform-observability | 13 / 13 | 100% |

These are per-module results, not whole-repository coverage. Gateway, auth and dashboard functionality has no test coverage claim here. Branch counts and named test results are available in the [verification record](evidence/local-2026-10-23/verification.json).

## Local measurement, with limits

The [raw result](evidence/local-2026-10-23/local-smoke.json) contains all 200 samples, timestamps and environment fields. Eight closed-loop workers sent HTTP requests to Spring Boot with H2 and an embedded Kafka broker. JaCoCo instrumentation was enabled, and the host exposed 12 logical processors.

| Metric | Observed |
| --- | ---: |
| Measured requests | 200 |
| Elapsed measured interval | 0.3316 seconds |
| Requests / second over that interval | 603.17 |
| p50 | 12.53 ms |
| p95 | 15.62 ms |
| p99 | 16.58 ms |
| Process CPU time during interval | 2.484 seconds across cores |
| Post-run heap usage | 90,846,960 bytes |

This very short run validates the measurement path and provides a local baseline. It does **not** establish sustained capacity, a latency SLO, a production improvement, or settlement throughput. No before/after optimization comparison, live PostgreSQL/Redis failover, multi-node Kafka resilience, k6 sustained-load run, or JFR analysis has been performed. See the [benchmark guide](BENCHMARKS.md) for those experiments.

## Provenance and reproduction

The working branch is `portfolio/evidence-and-reliability`, based on commit `c5fb3f7bc9f57318cb6504e01633e3a0a9a8adf4`. Changes were uncommitted when these results were collected. The verification record includes SHA-256 fingerprints of source/configuration files so the evidence is not attributed to the unchanged base commit.

To regenerate after a clean verification, choose a new output directory:

```sh
python performance/collect-evidence.py docs/evidence/my-new-run
```

The collector preserves named results and coverage counts from Surefire/JaCoCo and copies the benchmark JSON. It refuses to overwrite an existing evidence directory. Source reports remain under module `target/` directories. Logs/reports from later test runs must not be mixed with this record.
