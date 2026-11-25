package com.carenest.integration.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Web OIDC gateway (ADR-0013): đăng nhập, refresh, đổi/khôi phục mật khẩu qua identity provider. */
public interface AuthGatewayClient {

    Tokens login(String identifier, String password);

    /**
     * Keycloak brute-force đang chặn tài khoản của {@code identifier} tới mốc nào (AUTH-03 đăng nhập).
     * Keycloak trả lỗi giống sai mật khẩu khi đang chặn nên phải hỏi admin API; không tìm thấy, không bị chặn
     * hoặc lỗi tra cứu ⇒ rỗng (giữ thông báo sai mật khẩu, không chặn đăng nhập).
     */
    java.util.Optional<java.time.Instant> loginBlockedUntil(String identifier);

    Tokens refresh(String refreshToken);

    void logout(String refreshToken);

    /** Hủy mọi phiên Web gateway khác của user, giữ {@code keepSessionId}; trả id phiên đã hủy. Best-effort, không ném lỗi. */
    java.util.List<String> revokeOtherWebSessions(String keycloakUserId, String keepSessionId);

    /** User còn phiên Web gateway nào khác {@code keepSessionId} không; lỗi tra cứu ⇒ false (không chặn đăng nhập). */
    boolean hasOtherWebSessions(String keycloakUserId, String keepSessionId);

    /** Hủy một phiên Keycloak theo id; best-effort, không ném lỗi. */
    void revokeSession(String sessionId);

    /** Phiên của refresh token bị từ chối có phiên Web mới hơn (từ {@code loginAt}) thay thế không; lỗi tra cứu ⇒ false. */
    boolean replacedByNewerWebSession(String rejectedRefreshToken, java.time.Instant loginAt);

    /** Đọc {@code sub}/{@code sid} của refresh token gateway (do server niêm phong); không đọc được ⇒ rỗng. */
    java.util.Optional<SessionRef> sessionOf(String refreshToken);

    record SessionRef(String keycloakUserId, String sessionId) {}

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

    /** Đúng mật khẩu nhưng tài khoản đã bị vô hiệu hóa (Keycloak chỉ báo khi mật khẩu đúng). */
    final class AccountDisabled extends RuntimeException {
        AccountDisabled() {
            super("Account disabled");
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
