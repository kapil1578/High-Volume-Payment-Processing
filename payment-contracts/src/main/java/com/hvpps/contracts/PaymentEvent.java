package com.hvpps.contracts;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentEvent(
        String requestId,
        String accountId,
        BigDecimal amount,
        String currency,
        String merchantId,
        Instant createdAt
) {
}
