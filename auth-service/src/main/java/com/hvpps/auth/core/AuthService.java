package com.hvpps.auth.core;

import com.hvpps.auth.api.model.AuthResponse;
import com.hvpps.auth.api.model.LoginRequest;
import com.hvpps.auth.api.model.OtpSendRequest;
import com.hvpps.auth.api.model.OtpSendResponse;
import com.hvpps.auth.api.model.OtpVerifyRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final Map<String, String> otpSessions = new ConcurrentHashMap<>();
    private final Map<String, String> otpPhones = new ConcurrentHashMap<>();

    public AuthResponse loginWithPassword(LoginRequest request) {
        if (request.username().isBlank() || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required");
        }
        return successToken(request.username(), "password");
    }

    public OtpSendResponse sendOtp(OtpSendRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String otp = "123456";
        otpSessions.put(sessionId, otp);
        otpPhones.put(sessionId, request.mobileNumber());
        return new OtpSendResponse(sessionId, "OTP sent successfully", otp);
    }

    public AuthResponse verifyOtp(OtpVerifyRequest request) {
        String expectedOtp = otpSessions.get(request.otpSessionId());
        if (expectedOtp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP session not found");
        }
        if (!expectedOtp.equals(request.otpCode())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP");
        }
        String mobile = otpPhones.getOrDefault(request.otpSessionId(), "mobile-user");
        otpSessions.remove(request.otpSessionId());
        otpPhones.remove(request.otpSessionId());
        return successToken(mobile, "mobile-otp");
    }

    public AuthResponse loginWithProvider(String provider, String code) {
        String normalized = provider.toLowerCase(Locale.ROOT);
        if (!normalized.equals("google") && !normalized.equals("github")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider");
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authorization code required");
        }
        return successToken(normalized + "-user", normalized);
    }

    private AuthResponse successToken(String user, String method) {
        String token = "paydash-" + UUID.randomUUID();
        return new AuthResponse(token, user, method, "Login successful");
    }
}
