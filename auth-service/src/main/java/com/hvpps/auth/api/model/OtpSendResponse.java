package com.hvpps.auth.api.model;

public record OtpSendResponse(
        String otpSessionId,
        String message,
        String demoOtp
) {
}
