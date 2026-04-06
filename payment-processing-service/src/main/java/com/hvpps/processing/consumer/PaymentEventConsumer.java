package com.hvpps.processing.consumer;

import com.hvpps.contracts.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    @KafkaListener(topics = "payments.v1", groupId = "payment-processors-v1")
    public void consume(PaymentEvent event) {
        if ("FAIL".equalsIgnoreCase(event.merchantId())) {
            throw new IllegalStateException("Simulated downstream failure for retry and DLQ flow");
        }

        // This is where business rules, fraud checks, and downstream settlement calls run.
        log.info("Processing payment requestId={} accountId={} amount={} {}",
                event.requestId(), event.accountId(), event.amount(), event.currency());
    }
}
