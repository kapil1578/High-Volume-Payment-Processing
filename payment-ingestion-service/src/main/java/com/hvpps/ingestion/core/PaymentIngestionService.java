package com.hvpps.ingestion.core;

import com.hvpps.contracts.PaymentAcceptedResponse;
import com.hvpps.contracts.PaymentEvent;
import com.hvpps.contracts.PaymentRequest;
import com.hvpps.ingestion.idempotency.IdempotencyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PaymentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIngestionService.class);
    private static final String PAYMENT_TOPIC = "payments.v1";

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private final IdempotencyStore idempotencyStore;

    public PaymentIngestionService(KafkaTemplate<String, PaymentEvent> kafkaTemplate, IdempotencyStore idempotencyStore) {
        this.kafkaTemplate = kafkaTemplate;
        this.idempotencyStore = idempotencyStore;
    }

    public PaymentAcceptedResponse ingest(PaymentRequest request) {
        if (!idempotencyStore.registerIfAbsent(request.requestId())) {
            return new PaymentAcceptedResponse(
                    request.requestId(),
                    "DUPLICATE",
                    "Request already accepted previously"
            );
        }

        PaymentEvent event = new PaymentEvent(
                request.requestId(),
                request.accountId(),
                request.amount(),
                request.currency(),
                request.merchantId(),
                Instant.now()
        );

        kafkaTemplate.send(PAYMENT_TOPIC, request.requestId(), event);
        log.info("Accepted payment requestId={} accountId={} amount={}",
                request.requestId(), request.accountId(), request.amount());

        return new PaymentAcceptedResponse(
                request.requestId(),
                "ACCEPTED",
                "Payment event queued for async processing"
        );
    }
}
