package com.carenest.security;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.carenest.config.AuthProperties;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthCryptoTest {
    private final AuthCrypto crypto = new AuthCrypto(new AuthProperties(
            true,
            Base64.getEncoder().encodeToString(new byte[32]),
            Base64.getEncoder().encodeToString(new byte[32]),
            "carenest-auth-gateway",
            "test-client-secret-at-least-thirty-two-chars",
            false,
            List.of("http://localhost:5173")));

    @Test
    void encryptsWithRandomIvAndBindsToSessionAndPurpose() {
        String encrypted = assertDoesNotThrow(() -> crypto.seal("test-refresh-token", "session-a:refresh"));
        assertThat(encrypted).doesNotContain("test-refresh-token");
        assertThat(crypto.seal("test-refresh-token", "session-a:refresh")).isNotEqualTo(encrypted);
        assertThat(crypto.open(encrypted, "session-a:refresh")).isEqualTo("test-refresh-token");
        assertThatThrownBy(() -> crypto.open(encrypted, "session-b:refresh")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void issuesUnguessableTokensAndStoresOnlyFixedSizeDigest() {
        String token = assertDoesNotThrow(crypto::randomToken);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(crypto.hash(token)).hasSize(64).isNotEqualTo(token);
        assertThat(crypto.hash(token)).isEqualTo(crypto.hash(token));
    }

    @Test
    void otpDigestIsBoundToItsRequestAndVerifiedInConstantTime() {
        String digest = assertDoesNotThrow(() -> crypto.otpDigest("request-a", "012345"));
        assertThat(crypto.matchesOtp(digest, "request-a", "012345")).isTrue();
        assertThat(crypto.matchesOtp(digest, "request-b", "012345")).isFalse();
        assertThat(crypto.matchesOtp(digest, "request-a", "123456")).isFalse();
    }

    @Test
    void recoveryAssertionIsShortLivedPurposeBoundAndSigned() {
        String assertion = assertDoesNotThrow(() ->
                crypto.assertion("kc-user", "carenest", "RECOVERY_VERIFIED", Instant.parse("2026-10-12T00:00:00Z")));
        String[] parts = assertion.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isEqualTo("v1");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(claims).contains("RECOVERY_VERIFIED", "carenest-password-mutation", "kc-user");
        assertThat(parts[2]).hasSize(43);
    }
}
