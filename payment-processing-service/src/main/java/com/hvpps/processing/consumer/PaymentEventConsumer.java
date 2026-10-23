package com.hvpps.processing.consumer;
import com.hvpps.contracts.PaymentEvent;
import com.hvpps.platform.OperationMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {
    private final OperationMetrics metrics;
    public PaymentEventConsumer(MeterRegistry registry) {
        metrics = new OperationMetrics(registry, OperationMetrics.Operation.PROCESSING);
    }
    @KafkaListener(topics = "payments.v1", groupId = "payment-processors-v1")
    public void consume(PaymentEvent event) {
        long started = System.nanoTime();
        try {
            if ("FAIL".equalsIgnoreCase(event.merchantId())) {
                throw new IllegalStateException("Simulated downstream failure for retry and DLQ flow");
            }
            // Demonstration handler only: no financial settlement or exactly-once ledger exists here.
            metrics.record(OperationMetrics.Outcome.SUCCESS, started);
        } catch (RuntimeException ex) {
            metrics.record(OperationMetrics.Outcome.FAILURE, started);
            throw ex;
        }
    }
}
