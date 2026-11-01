package com.carenest.dto.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public final class AuthResponses {
    private AuthResponses() {}

    public record Csrf(String token, String headerName) {
        @Override
        public String toString() {
            return "Csrf[redacted]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginData(
            String accessToken,
            Instant expiresAt,
            boolean passwordChangeRequired,
            String changeToken,
            MyAccountResponse account) {
        @Override
        public String toString() {
            return "LoginData[redacted]";
        }
    }

    public record AccessToken(String accessToken, Instant expiresAt) {
        @Override
        public String toString() {
            return "AccessToken[redacted]";
        }
    }

    public record OtpRequest(String requestId, Instant expiresAt, Instant resendAvailableAt) {
        @Override
        public String toString() {
            return "OtpRequest[redacted]";
        }
    }

    public record ResetToken(String resetToken, Instant expiresAt) {
        @Override
        public String toString() {
            return "ResetToken[redacted]";
        }
    }
}
