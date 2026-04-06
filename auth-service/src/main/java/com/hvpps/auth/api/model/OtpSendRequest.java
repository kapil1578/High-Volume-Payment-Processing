package com.hvpps.auth.api.model;

import jakarta.validation.constraints.NotBlank;

public record OtpSendRequest(
        @NotBlank String mobileNumber
) {
}
