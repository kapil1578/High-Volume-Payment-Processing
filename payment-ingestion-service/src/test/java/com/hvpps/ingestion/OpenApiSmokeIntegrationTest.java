package com.hvpps.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "hvpps.idempotency.store=redis",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379"
        }
)
@EmbeddedKafka(partitions = 1, topics = "payments.v1")
class OpenApiSmokeIntegrationTest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    void apiDocsEndpointExposesPaymentsContract() {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"openapi\"");
        assertThat(response.getBody()).contains("/api/payments");
    }
}
