package com.hvpps.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic paymentsTopic() {
        return TopicBuilder.name("payments.v1")
                .partitions(12)
                .replicas(1)
                .build();
    }
}
