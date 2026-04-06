package com.hvpps.dashboard.core;

import com.hvpps.dashboard.core.model.DashboardOverview;
import com.hvpps.dashboard.core.model.FailedTxn;
import com.hvpps.dashboard.core.model.KafkaPartitionStats;
import com.hvpps.dashboard.core.model.LiveMetric;
import com.hvpps.dashboard.core.model.TelemetryPoint;
import com.hvpps.dashboard.core.model.TelemetrySeries;
import com.hvpps.dashboard.core.model.TransactionDetail;
import com.hvpps.dashboard.core.model.TransactionItem;
import com.hvpps.dashboard.core.model.TransactionTimelineEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DashboardDataService {

    private final Map<String, TransactionDetail> transactionDetails = new ConcurrentHashMap<>();
    private final List<String> recentTransactionIds = new ArrayList<>();
    private final List<String> catalogedTransactionIds = new ArrayList<>();
    private int transactionSequence = 49214;
    private long lastTransactionMutationEpochMs = System.currentTimeMillis();

    public DashboardDataService() {
        TransactionDetail tx1 = new TransactionDetail(
                    "TXN-0049213", "Transfer", 4250.00, "USD", "ACC-8821", "ACC-3304", "Success", 14,
                    "REST", "Low", "trc-a1293f", "proc-99411", "2026-04-06T12:20:14Z", "2026-04-06T12:20:18Z", null,
                    List.of(
                            new TransactionTimelineEvent("2026-04-06T12:20:14Z", "Accepted", "Request accepted by gateway"),
                            new TransactionTimelineEvent("2026-04-06T12:20:15Z", "Queued", "Published to Kafka topic payments.v1"),
                            new TransactionTimelineEvent("2026-04-06T12:20:17Z", "Processed", "Processing service completed business rules"),
                            new TransactionTimelineEvent("2026-04-06T12:20:18Z", "Settled", "Downstream settlement successful")
                    )
            );
        TransactionDetail tx2 = new TransactionDetail(
                    "TXN-0049212", "Payment", 182.50, "USD", "ACC-1192", "MERCH-771", "Success", 21,
                    "REST", "Medium", "trc-b293ad", "proc-99412", "2026-04-06T12:21:06Z", "2026-04-06T12:21:10Z", null,
                    List.of(
                            new TransactionTimelineEvent("2026-04-06T12:21:06Z", "Accepted", "Request accepted by gateway"),
                            new TransactionTimelineEvent("2026-04-06T12:21:08Z", "Processed", "Fraud and AML checks passed"),
                            new TransactionTimelineEvent("2026-04-06T12:21:10Z", "Settled", "Merchant capture successful")
                    )
            );
        TransactionDetail tx3 = new TransactionDetail(
                    "TXN-0049211", "Refund", 99.00, "USD", "MERCH-440", "ACC-6650", "Pending", 0,
                    "REST", "Low", "trc-c8121f", "proc-99413", "2026-04-06T12:21:55Z", null, null,
                    List.of(
                            new TransactionTimelineEvent("2026-04-06T12:21:55Z", "Accepted", "Refund request accepted"),
                            new TransactionTimelineEvent("2026-04-06T12:21:58Z", "Queued", "Waiting for settlement window")
                    )
            );
        TransactionDetail tx4 = new TransactionDetail(
                    "TXN-0049210", "Transfer", 12000.00, "USD", "ACC-0033", "ACC-9981", "Failed", 0,
                    "REST", "High", "trc-d19a03", "proc-99414", "2026-04-06T12:22:40Z", null, "Settlement timeout after retries",
                    List.of(
                            new TransactionTimelineEvent("2026-04-06T12:22:40Z", "Accepted", "Request accepted"),
                            new TransactionTimelineEvent("2026-04-06T12:22:42Z", "Processed", "Business checks passed"),
                            new TransactionTimelineEvent("2026-04-06T12:22:45Z", "Retrying", "Downstream timeout - retry attempt 1"),
                            new TransactionTimelineEvent("2026-04-06T12:22:49Z", "Retrying", "Downstream timeout - retry attempt 2"),
                            new TransactionTimelineEvent("2026-04-06T12:22:53Z", "Failed", "Moved to DLQ after max retries")
                    )
            );
        TransactionDetail tx5 = new TransactionDetail(
                    "TXN-0049209", "Payment", 56.75, "USD", "ACC-5543", "MERCH-210", "Success", 11,
                    "REST", "Low", "trc-e552c9", "proc-99415", "2026-04-06T12:23:12Z", "2026-04-06T12:23:15Z", null,
                    List.of(
                            new TransactionTimelineEvent("2026-04-06T12:23:12Z", "Accepted", "Request accepted"),
                            new TransactionTimelineEvent("2026-04-06T12:23:14Z", "Processed", "Payment authorized"),
                            new TransactionTimelineEvent("2026-04-06T12:23:15Z", "Settled", "Funds captured")
                    )
            );

        seedTransaction(tx1);
        seedTransaction(tx2);
        seedTransaction(tx3);
        seedTransaction(tx4);
        seedTransaction(tx5);
    }

    public DashboardOverview overview() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new DashboardOverview(
                r.nextInt(980, 1350),
                99.8,
                r.nextInt(12, 25),
                r.nextInt(1, 6)
        );
    }

    public List<TransactionItem> transactions() {
        synchronized (this) {
            maybeMutateTransactions();
            return toTransactionItems(recentTransactionIds);
        }
    }

    public List<TransactionItem> catalogedTransactions() {
        synchronized (this) {
            maybeMutateTransactions();
            return toTransactionItems(catalogedTransactionIds);
        }
    }

    public List<KafkaPartitionStats> partitions() {
        return List.of(
                new KafkaPartitionStats("payments-in", 920),
                new KafkaPartitionStats("payments-out", 882),
                new KafkaPartitionStats("audit-log", 741),
                new KafkaPartitionStats("dead-letter", 2)
        );
    }

    public List<FailedTxn> failedTransactions() {
        return List.of(
                new FailedTxn("TXN-0049210", "Settlement timeout", "2026-04-06T12:34:51Z"),
                new FailedTxn("TXN-0049202", "Risk rule violation", "2026-04-06T12:30:03Z")
        );
    }

    public LiveMetric liveMetric() {
        DashboardOverview overview = overview();
        return new LiveMetric(
                overview.throughputPerSecond(),
                overview.failedTransactions(),
                overview.avgLatencyMs()
        );
    }

    public TelemetrySeries telemetry(String range) {
        String normalized = range == null ? "hour" : range.toLowerCase(Locale.ROOT);
        int pointsCount = switch (normalized) {
            case "second" -> 60;
            case "minute" -> 60;
            case "hour" -> 24;
            case "day" -> 30;
            case "month" -> 12;
            case "year" -> 10;
            case "decade" -> 10;
            default -> 24;
        };

        int min = switch (normalized) {
            case "second", "minute" -> 900;
            case "hour" -> 850;
            case "day" -> 800;
            case "month" -> 720;
            case "year", "decade" -> 650;
            default -> 850;
        };
        int max = switch (normalized) {
            case "second", "minute" -> 1450;
            case "hour" -> 1400;
            case "day" -> 1380;
            case "month" -> 1320;
            case "year", "decade" -> 1260;
            default -> 1400;
        };

        List<TelemetryPoint> points = new ArrayList<>(pointsCount);
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = switch (normalized) {
            case "second" -> DateTimeFormatter.ofPattern("HH:mm:ss");
            case "minute" -> DateTimeFormatter.ofPattern("HH:mm");
            case "hour" -> DateTimeFormatter.ofPattern("HH:mm");
            case "day" -> DateTimeFormatter.ofPattern("dd MMM");
            case "month" -> DateTimeFormatter.ofPattern("MMM yyyy");
            case "year", "decade" -> DateTimeFormatter.ofPattern("yyyy");
            default -> DateTimeFormatter.ofPattern("HH:mm");
        };

        for (int i = pointsCount - 1; i >= 0; i--) {
            LocalDateTime t = switch (normalized) {
                case "second" -> now.minusSeconds(i);
                case "minute" -> now.minusMinutes(i);
                case "hour" -> now.minusHours(i);
                case "day" -> now.minusDays(i);
                case "month" -> now.minusMonths(i);
                case "year" -> now.minusYears(i);
                case "decade" -> now.minusYears(i * 10L);
                default -> now.minusHours(i);
            };
            int tps = ThreadLocalRandom.current().nextInt(min, max);
            points.add(new TelemetryPoint(t.format(formatter), tps));
        }
        return new TelemetrySeries(normalized, points);
    }

    public TransactionDetail transactionDetail(String transactionId) {
        TransactionDetail detail = transactionDetails.get(transactionId);
        if (detail == null) {
            throw new ResponseStatusException(NOT_FOUND, "Transaction not found");
        }
        return detail;
    }

    private void seedTransaction(TransactionDetail detail) {
        transactionDetails.put(detail.transactionId(), detail);
        recentTransactionIds.add(detail.transactionId());
    }

    private TransactionItem toItem(TransactionDetail detail) {
        return new TransactionItem(
                detail.transactionId(),
                detail.type(),
                detail.amount(),
                detail.source(),
                detail.destination(),
                detail.status(),
                detail.latencyMs()
        );
    }

    private void maybeMutateTransactions() {
        long now = System.currentTimeMillis();
        if (now - lastTransactionMutationEpochMs < 3_000L) {
            return;
        }
        lastTransactionMutationEpochMs = now;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int size = recentTransactionIds.size();
        if (size < 4) {
            prependNewTransaction(random);
            return;
        }
        if (size > 8) {
            removeOldestTransaction();
            return;
        }

        int action = random.nextInt(100);
        if (action < 55) {
            prependNewTransaction(random);
        } else if (action < 90) {
            removeOldestTransaction();
        } else {
            prependNewTransaction(random);
            removeOldestTransaction();
        }
    }

    private void prependNewTransaction(ThreadLocalRandom random) {
        String transactionId = String.format("TXN-%07d", ++transactionSequence);
        String type = pick(random, "Payment", "Transfer", "Refund");
        String status = pick(random, "Success", "Pending", "Failed");
        int latencyMs = "Pending".equals(status) ? 0 : random.nextInt(9, 60);
        double amount = Math.round(random.nextDouble(25.0, 15000.0) * 100.0) / 100.0;
        String source = type.equals("Refund") ? "MERCH-" + random.nextInt(100, 999) : "ACC-" + random.nextInt(1000, 9999);
        String destination = type.equals("Refund") ? "ACC-" + random.nextInt(1000, 9999) : "MERCH-" + random.nextInt(100, 999);
        String createdAt = LocalDateTime.now().minusSeconds(random.nextInt(2, 15)).toInstant(ZoneOffset.UTC).toString();
        String settledAt = "Success".equals(status) ? LocalDateTime.now().toInstant(ZoneOffset.UTC).toString() : null;
        String failureReason = "Failed".equals(status) ? pick(random, "Issuer rejected", "Settlement timeout", "Risk rule violation") : null;

        List<TransactionTimelineEvent> timeline = new ArrayList<>();
        timeline.add(new TransactionTimelineEvent(createdAt, "Accepted", "Request accepted by gateway"));
        timeline.add(new TransactionTimelineEvent(LocalDateTime.now().minusSeconds(1).toInstant(ZoneOffset.UTC).toString(), "Processed", "Validated by processing service"));
        if ("Success".equals(status)) {
            timeline.add(new TransactionTimelineEvent(settledAt, "Settled", "Funds movement completed"));
        } else if ("Pending".equals(status)) {
            timeline.add(new TransactionTimelineEvent(LocalDateTime.now().toInstant(ZoneOffset.UTC).toString(), "Queued", "Awaiting downstream confirmation"));
        } else {
            timeline.add(new TransactionTimelineEvent(LocalDateTime.now().toInstant(ZoneOffset.UTC).toString(), "Failed", failureReason));
        }

        TransactionDetail detail = new TransactionDetail(
                transactionId,
                type,
                amount,
                "USD",
                source,
                destination,
                status,
                latencyMs,
                "REST",
                pick(random, "Low", "Medium", "High"),
                "trc-" + Integer.toHexString(random.nextInt(100_000, 999_999)),
                "proc-" + random.nextInt(99_500, 99_999),
                createdAt,
                settledAt,
                failureReason,
                timeline
        );

        transactionDetails.put(transactionId, detail);
        recentTransactionIds.add(0, transactionId);
    }

    private void removeOldestTransaction() {
        if (recentTransactionIds.size() <= 1) {
            return;
        }
        int lastIndex = recentTransactionIds.size() - 1;
        String removedId = recentTransactionIds.remove(lastIndex);
        catalogedTransactionIds.add(0, removedId);
        if (catalogedTransactionIds.size() > 30) {
            String oldestCataloged = catalogedTransactionIds.remove(catalogedTransactionIds.size() - 1);
            transactionDetails.remove(oldestCataloged);
        }
    }

    private List<TransactionItem> toTransactionItems(List<String> transactionIds) {
        List<TransactionItem> items = new ArrayList<>(transactionIds.size());
        for (String transactionId : transactionIds) {
            TransactionDetail detail = transactionDetails.get(transactionId);
            if (detail != null) {
                items.add(toItem(detail));
            }
        }
        return Collections.unmodifiableList(items);
    }

    private String pick(ThreadLocalRandom random, String... values) {
        return values[random.nextInt(values.length)];
    }
}
