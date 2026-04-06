package com.hvpps.auth.api.model;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
        @NotBlank String otpSessionId,
        @NotBlank String otpCode
) {
}
