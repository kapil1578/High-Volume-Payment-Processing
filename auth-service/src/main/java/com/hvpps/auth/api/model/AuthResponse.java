package com.hvpps.auth.api.model;

public record AuthResponse(
        String token,
        String displayName,
        String loginMethod,
        String message
) {
}
