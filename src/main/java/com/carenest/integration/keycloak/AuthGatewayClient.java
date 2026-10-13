package com.carenest.integration.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Web OIDC gateway (ADR-0013): đăng nhập, refresh, đổi/khôi phục mật khẩu qua identity provider. */
public interface AuthGatewayClient {

    Tokens login(String identifier, String password);

    Tokens refresh(String refreshToken);

    void logout(String refreshToken);

    String verifiedCapability(String assertion);

    void changePassword(String capability, String newPassword, String confirmation);

    void sendOtp(String keycloakUserId, String code);

    void sendReactSetupEmail(String keycloakUserId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tokens(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_expires_in") long refreshExpiresIn) {
        @Override
        public String toString() {
            return "Tokens[redacted]";
        }
    }

    final class FirstLoginRequired extends RuntimeException {
        private final String userId;
        private final String setupToken;
        private final long expiresIn;

        FirstLoginRequired(String userId, String setupToken, long expiresIn) {
            super("Password change required");
            this.userId = userId;
            this.setupToken = setupToken;
            this.expiresIn = expiresIn;
        }

        public String userId() {
            return userId;
        }

        public String setupToken() {
            return setupToken;
        }

        public long expiresIn() {
            return expiresIn;
        }

        @Override
        public String toString() {
            return "FirstLoginRequired[redacted]";
        }
    }

    final class InvalidCredentials extends RuntimeException {
        InvalidCredentials() {
            super("Invalid credentials");
        }
    }

    final class SessionRejected extends RuntimeException {
        SessionRejected() {
            super("Web session rejected by provider");
        }
    }

    final class ProviderUnavailable extends RuntimeException {
        ProviderUnavailable() {
            super("Authentication provider unavailable");
        }
    }

    abstract class ProviderFailure extends RuntimeException {
        ProviderFailure(String message) {
            super(message);
        }
    }

    final class PasswordRejected extends ProviderFailure {
        PasswordRejected() {
            super("Password rejected by provider policy");
        }
    }

    final class CapabilityRejected extends ProviderFailure {
        CapabilityRejected() {
            super("Password capability is invalid or expired");
        }
    }
}
