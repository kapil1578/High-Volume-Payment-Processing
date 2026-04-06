package com.hvpps.ingestion;

import com.hvpps.contracts.PaymentAcceptedResponse;
import com.hvpps.contracts.PaymentRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "hvpps.idempotency.store=sql",
                "spring.datasource.url=jdbc:h2:file:./target/idempotency-restart-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always"
        }
)
@EmbeddedKafka(partitions = 1, topics = "payments.v1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentIdempotencyRestartIntegrationTest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @BeforeAll
    static void cleanupPreviousDb() throws Exception {
        Files.deleteIfExists(Path.of("target", "idempotency-restart-it.mv.db"));
        Files.deleteIfExists(Path.of("target", "idempotency-restart-it.trace.db"));
    }

    @Test
    @Order(1)
    void firstRequestIsAccepted() {
        ResponseEntity<PaymentAcceptedResponse> response = postPayment("req-restart-001");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("ACCEPTED");
    }

    @Test
    @Order(2)
    void duplicateRequestIsRejectedAfterContextRestart() {
        ResponseEntity<PaymentAcceptedResponse> response = postPayment("req-restart-001");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DUPLICATE");
    }

    private ResponseEntity<PaymentAcceptedResponse> postPayment(String requestId) {
        PaymentRequest request = new PaymentRequest(
                requestId,
                "acct-it-01",
                java.math.BigDecimal.valueOf(19.99),
                "USD",
                "mrc-it"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

        return testRestTemplate.postForEntity("/api/payments", entity, PaymentAcceptedResponse.class);
    }
}
