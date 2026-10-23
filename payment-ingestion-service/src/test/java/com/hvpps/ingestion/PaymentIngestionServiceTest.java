package com.hvpps.ingestion;

import com.hvpps.contracts.PaymentEvent;
import com.hvpps.contracts.PaymentRequest;
import com.hvpps.ingestion.core.PaymentIngestionService;
import com.hvpps.ingestion.idempotency.IdempotencyStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentIngestionServiceTest {
    private final PaymentRequest request = new PaymentRequest("req-test", "account", new BigDecimal("12.50"), "USD", "merchant");
    private KafkaTemplate<String, PaymentEvent> kafka;
    private IdempotencyStore store;
    private SimpleMeterRegistry registry;
    private PaymentIngestionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafka = mock(KafkaTemplate.class);
        store = mock(IdempotencyStore.class);
        registry = new SimpleMeterRegistry();
        service = new PaymentIngestionService(kafka, store, registry, 5000);
        when(store.registerIfAbsent(request.requestId())).thenReturn(true);
    }

    @Test
    void doesNotAcceptUntilBrokerAcknowledges() throws Exception {
        var acknowledgement = new CompletableFuture<SendResult<String, PaymentEvent>>();
        var sendEntered = new java.util.concurrent.CountDownLatch(1);
        when(kafka.send(eq("payments.v1"), eq(request.requestId()), any(PaymentEvent.class)))
                .thenAnswer(invocation -> { sendEntered.countDown(); return acknowledgement; });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var response = executor.submit(() -> service.ingest(request));
            assertThat(sendEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(response.isDone()).isFalse();
            acknowledgement.complete(null);
            assertThat(response.get(2, TimeUnit.SECONDS).status()).isEqualTo("ACCEPTED");
            assertThat(registry.get("hvpps.operation").tag("outcome", "success").timer().count()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void duplicateDoesNotPublishOrClaimAcceptance() {
        when(store.registerIfAbsent(request.requestId())).thenReturn(false);
        assertThat(service.ingest(request).status()).isEqualTo("DUPLICATE");
        verifyNoInteractions(kafka);
    }

    @Test
    void asynchronousBrokerFailureReturnsUnavailable() {
        when(kafka.send(anyString(), anyString(), any(PaymentEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        assertUnavailable();
    }

    @Test
    void synchronousProducerFailureReturnsUnavailable() {
        when(kafka.send(anyString(), anyString(), any(PaymentEvent.class)))
                .thenThrow(new IllegalStateException("metadata unavailable"));
        assertUnavailable();
    }

    @Test
    void unacknowledgedPublicationTimesOut() {
        service = new PaymentIngestionService(kafka, store, registry, 10);
        when(kafka.send(anyString(), anyString(), any(PaymentEvent.class))).thenReturn(new CompletableFuture<>());
        assertUnavailable();
    }

    @Test
    void unavailableDedupeStoreFailsClosed() {
        when(store.registerIfAbsent(request.requestId())).thenThrow(new IllegalStateException("database unavailable"));
        assertUnavailable();
        verifyNoInteractions(kafka);
    }

    @Test
    void retryAfterUnknownOutcomeDoesNotRepublish() {
        service = new PaymentIngestionService(kafka, store, registry, 10);
        when(store.registerIfAbsent(request.requestId())).thenReturn(true, false);
        when(kafka.send(anyString(), anyString(), any(PaymentEvent.class))).thenReturn(new CompletableFuture<>());
        assertUnavailable();
        assertThat(service.ingest(request).status()).isEqualTo("DUPLICATE");
        verify(kafka, times(1)).send(anyString(), anyString(), any(PaymentEvent.class));
    }

    @Test
    void preservesThreadInterruption() {
        when(kafka.send(anyString(), anyString(), any(PaymentEvent.class))).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();
        try {
            assertUnavailable();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> service.ingest(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
        assertThat(registry.get("hvpps.operation").tag("outcome", "failure").timer().count()).isEqualTo(1);
    }
}
