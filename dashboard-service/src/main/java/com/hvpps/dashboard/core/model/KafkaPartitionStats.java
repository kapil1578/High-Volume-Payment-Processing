package com.hvpps.dashboard.core.model;

public record KafkaPartitionStats(
        String topic,
        int messagesPerSecond
) {
}
