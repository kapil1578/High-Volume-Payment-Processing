package com.hvpps.processing.config;

import com.hvpps.contracts.PaymentEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public NewTopic paymentDlqTopic() {
        return TopicBuilder.name("payments.v1.dlq")
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, PaymentEvent> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, ex) -> new TopicPartition("payments.v1.dlq", record.partition())
        );

        // Retry 3 times with 1s backoff; then send to DLQ.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
