package com.hvpps.ingestion.core;

import com.hvpps.contracts.PaymentAcceptedResponse;
import com.hvpps.contracts.PaymentEvent;
import com.hvpps.contracts.PaymentRequest;
import com.hvpps.ingestion.idempotency.IdempotencyStore;
import com.hvpps.platform.OperationMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class PaymentIngestionService {
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private final IdempotencyStore idempotencyStore;
    private final OperationMetrics metrics;
    private final long publishTimeoutMs;

    public PaymentIngestionService(KafkaTemplate<String, PaymentEvent> kafkaTemplate,
            IdempotencyStore idempotencyStore, MeterRegistry registry,
            @Value("${hvpps.publish-timeout-ms:5000}") long publishTimeoutMs) {
        if (publishTimeoutMs <= 0) throw new IllegalArgumentException("publish timeout must be positive");
        this.kafkaTemplate = kafkaTemplate;
        this.idempotencyStore = idempotencyStore;
        this.metrics = new OperationMetrics(registry, OperationMetrics.Operation.INGESTION);
        this.publishTimeoutMs = publishTimeoutMs;
    }

    public PaymentAcceptedResponse ingest(PaymentRequest request) {
        long started = System.nanoTime();
        try {
            if (!idempotencyStore.registerIfAbsent(request.requestId())) {
                metrics.record(OperationMetrics.Outcome.DUPLICATE, started);
                return new PaymentAcceptedResponse(request.requestId(), "DUPLICATE",
                        "Request ID already registered; this does not confirm publication or settlement");
            }
            PaymentEvent event = new PaymentEvent(request.requestId(), request.accountId(),
                    request.amount(), request.currency(), request.merchantId(), Instant.now());
            // ACCEPTED means Kafka acknowledged publication, not that settlement completed.
            kafkaTemplate.send("payments.v1", request.requestId(), event)
                    .get(publishTimeoutMs, TimeUnit.MILLISECONDS);
            metrics.record(OperationMetrics.Outcome.SUCCESS, started);
            return new PaymentAcceptedResponse(request.requestId(), "ACCEPTED",
                    "Kafka acknowledged the event; processing remains asynchronous");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            metrics.record(OperationMetrics.Outcome.FAILURE, started);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Publication interrupted; reconcile request ID", ex);
        } catch (Exception ex) {
            metrics.record(OperationMetrics.Outcome.FAILURE, started);
            // Keep the reservation: timeout may mean Kafka committed but the acknowledgement was lost.
            // Automatic key deletion/retry could duplicate a payment. A transactional outbox is future work.
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Publication unconfirmed; reconcile request ID", ex);
        }
    }
}
