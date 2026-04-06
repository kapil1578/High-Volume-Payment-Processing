package com.hvpps.processing;

import com.hvpps.contracts.PaymentEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@EmbeddedKafka(partitions = 1, topics = {"payments.v1", "payments.v1.dlq"})
class PaymentRetryDlqIntegrationTest {

    @Autowired
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void failedPaymentIsRetriedThenPublishedToDlq() {
        PaymentEvent event = new PaymentEvent(
                "req-dlq-001",
                "acct-dlq",
                java.math.BigDecimal.valueOf(10.00),
                "USD",
                "FAIL",
                Instant.now()
        );

        Consumer<String, PaymentEvent> dlqConsumer = buildDlqConsumer();
        dlqConsumer.subscribe(Collections.singletonList("payments.v1.dlq"));

        kafkaTemplate.send("payments.v1", event.requestId(), event);
        kafkaTemplate.flush();

        ConsumerRecord<String, PaymentEvent> dlqRecord = pollForDlqRecord(dlqConsumer, Duration.ofSeconds(20));
        assertThat(dlqRecord).isNotNull();
        assertThat(dlqRecord.key()).isEqualTo(event.requestId());
        assertThat(dlqRecord.value()).isNotNull();
        assertThat(dlqRecord.value().requestId()).isEqualTo(event.requestId());
    }

    private Consumer<String, PaymentEvent> buildDlqConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "dlq-test-consumer",
                "true",
                embeddedKafkaBroker
        );
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<PaymentEvent> valueDeserializer = new JsonDeserializer<>(PaymentEvent.class);
        valueDeserializer.addTrustedPackages("com.hvpps.contracts");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                valueDeserializer
        ).createConsumer();
    }

    private ConsumerRecord<String, PaymentEvent> pollForDlqRecord(
            Consumer<String, PaymentEvent> consumer,
            Duration timeout
    ) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, PaymentEvent> records = consumer.poll(Duration.ofSeconds(1));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        return null;
    }
}
