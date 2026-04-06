package com.hvpps.ingestion.api;

import com.hvpps.contracts.PaymentAcceptedResponse;
import com.hvpps.contracts.PaymentRequest;
import com.hvpps.ingestion.core.PaymentIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentIngestionService paymentIngestionService;

    public PaymentController(PaymentIngestionService paymentIngestionService) {
        this.paymentIngestionService = paymentIngestionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Ingest a payment request",
            description = "Accepts a client request and publishes an async payment event to Kafka."
    )
    @ApiResponse(responseCode = "202", description = "Payment accepted for async processing")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request payload",
            content = @Content(schema = @Schema(hidden = true))
    )
    public PaymentAcceptedResponse ingest(@Valid @RequestBody PaymentRequest request) {
        return paymentIngestionService.ingest(request);
    }
}
