# Reproduce and interpret the evidence

## Local smoke measurement

Requires Java 17+ and Maven. No Docker is required for the embedded tests:

```sh
mvn -B -ntp -Dportfolio.benchmark=true verify
```

The opt-in test writes `payment-ingestion-service/target/evidence/local-smoke.json`. It sends 20 warmup requests, then 200 measured requests with eight workers over real local HTTP to Spring Boot, SQL deduplication in H2, and an embedded Kafka broker. Every response must acknowledge the same request ID. It saves all latency samples, nearest-rank percentiles, elapsed throughput, process CPU time, Java/OS information, and a post-run heap snapshot.

This is a short, closed-loop smoke measurement in one JVM with JaCoCo instrumentation, a shared machine, and embedded dependencies. It excludes executor queue time from per-request latency, but includes all measured work in elapsed throughput. The CPU measurement includes the embedded broker and application. Heap after the run is neither peak memory nor memory per request. Results cannot substantiate sustained 1,000 TPS, production latency, settlement throughput, or a performance improvement over the original code.

## Sustained arrival-rate benchmark

Start Kafka, the selected idempotency store, ingestion and processing services. Use a fresh namespace/run ID, isolated test data, and the same service configuration for each comparison. Install k6 and Python 3, then run:

```sh
python performance/run-benchmark.py --rate 100 --duration 2m
python performance/run-benchmark.py --rate 500 --duration 5m
python performance/run-benchmark.py --rate 1000 --duration 5m
```

The runner saves the exact command, commit, dirty working-tree status, load-generator environment, k6 version, log, exit status, JSON summary and periodic Prometheus snapshots under `performance/results/<run-id>/`. No environment variables or credentials are dumped. Do not pass credentials in the base URL. Record the service hardware, replica count, JVM flags, Kafka partitions/replication, store choice and settings alongside each result. An unavailable metrics endpoint is recorded as a scrape error, not silently treated as healthy.

Thresholds require every response check to pass, zero dropped iterations, HTTP error rate below 1%, p95 below 250 ms and p99 below 500 ms. These are experiment criteria, not achieved claims. Duplicate responses fail the acceptance check. A generator that cannot sustain the requested rate fails the dropped-iteration check. The runner propagates the k6 exit code.

Run one warmup and at least three comparable measured trials per configuration. Report individual results and spread; keep failures. Change one variable at a time, such as `KAFKA_LINGER_MS` or consumer concurrency. Compare acknowledged requests/second, p95/p99, error rate, drops, CPU, heap/GC, and consumer lag. This runner does not collect consumer lag or settlement latency; obtain Kafka group lag separately before making end-to-end claims. Verify output records/counts separately from HTTP acceptance.

## JVM profiling experiment

Use `jcmd -l` to find the ingestion JVM you started. Replace `<pid>` with that PID:

```sh
jcmd <pid> JFR.start name=payments settings=profile duration=60s filename=payments.jfr
```

Run the same load during recording. Inspect the recording with Java Mission Control: allocation hot spots, GC pauses, blocked threads, JDBC pool contention and Kafka producer waits. Archive the recording with configuration and result JSON. Measure instrumented and uninstrumented runs separately. No profile or optimization claim is established until a recording has actually been inspected.

## Operational checks

- `GET /actuator/health` checks configured dependencies; it does not prove business-data consistency.
- `GET /actuator/prometheus` on ports 8081 and 8082 exposes actual JVM/HTTP and `hvpps_operation_seconds_*` metrics. Outcome success on ingestion means broker acknowledgement; processing success means the demo handler returned.
- With SQL mode, inspect unique reservations and their relationship to published records. There is no settlement table to reconcile yet.
- Send `merchantId=FAIL` to the test environment to exercise three retries and DLQ forwarding. DLQ publication failures must not be treated as successful recovery.
- Never clear reservation keys merely because a client timed out. Inspect Kafka and the original request, establish the outcome, and choose an explicit recovery procedure. Automated reconciliation remains unimplemented.
