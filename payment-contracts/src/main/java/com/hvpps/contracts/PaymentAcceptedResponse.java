package com.hvpps.contracts;

public record PaymentAcceptedResponse(
        String requestId,
        String status,
        String message
) {
}
