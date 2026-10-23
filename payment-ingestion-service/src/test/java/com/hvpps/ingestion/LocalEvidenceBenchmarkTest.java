package com.hvpps.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.test.context.EmbeddedKafka;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Local smoke measurement, deliberately not a production capacity claim. */
@EnabledIfSystemProperty(named = "portfolio.benchmark", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "hvpps.idempotency.store=sql", "spring.sql.init.mode=always",
        "spring.datasource.url=jdbc:h2:mem:benchmark;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jmx.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = "payments.v1")
class LocalEvidenceBenchmarkTest {
    @LocalServerPort int port;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void recordsHttpToKafkaAcknowledgementSmokeMeasurement() throws Exception {
        String run = UUID.randomUUID().toString();
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        for (int i = 0; i < 20; i++) send(client, run + "-warmup-" + i);
        var workers = Executors.newFixedThreadPool(8);
        var samples = new ArrayList<Future<Double>>();
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long cpuBefore = os.getProcessCpuTime();
        long started = System.nanoTime();
        List<Double> latencies = new ArrayList<>();
        try {
            for (int i = 0; i < 200; i++) {
                String id = run + "-" + i;
                samples.add(workers.submit(() -> send(client, id)));
            }
            for (var sample : samples) latencies.add(sample.get(30, TimeUnit.SECONDS));
        } finally { workers.shutdownNow(); }
        double elapsed = (System.nanoTime() - started) / 1e9;
        var sorted = latencies.stream().sorted().toList();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("timestampUtc", Instant.now().toString());
        evidence.put("scope", "Local HTTP ingestion to embedded Kafka ack; H2; 8 closed-loop workers; JaCoCo enabled; no settlement");
        evidence.put("runId", run);
        evidence.put("java", System.getProperty("java.runtime.version"));
        evidence.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        evidence.put("logicalProcessors", os.getAvailableProcessors());
        evidence.put("warmupRequests", 20);
        evidence.put("requests", latencies.size());
        evidence.put("concurrency", 8);
        evidence.put("elapsedSeconds", elapsed);
        evidence.put("requestsPerSecond", latencies.size() / elapsed);
        evidence.put("p50Ms", sorted.get(99));
        evidence.put("p95Ms", sorted.get(189));
        evidence.put("p99Ms", sorted.get(197));
        evidence.put("processCpuSeconds", (os.getProcessCpuTime() - cpuBefore) / 1e9);
        evidence.put("heapUsedBytesAfter", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        evidence.put("rawLatencyMs", latencies);
        Path output = Path.of("target", "evidence", "local-smoke.json");
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), evidence);
    }

    private double send(HttpClient client, String id) throws Exception {
        String body = json.writeValueAsString(Map.of("requestId", id, "accountId", "test-account",
                "amount", 12.50, "currency", "USD", "merchantId", "test-merchant"));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/payments"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        long start = System.nanoTime();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        double latency = (System.nanoTime() - start) / 1e6;
        assertThat(response.statusCode()).isEqualTo(202);
        var accepted = json.readTree(response.body());
        assertThat(accepted.path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(accepted.path("requestId").asText()).isEqualTo(id);
        return latency;
    }
}
