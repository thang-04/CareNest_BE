package com.carenest.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.keycloak.models.*;
import org.keycloak.util.JsonSerialization;

class VerifiedPasswordAssertionsTest {
    private final byte[] secret = new byte[32];
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);
    private final RealmModel realm = mock(RealmModel.class);
    private final SingleUseObjectProvider cache = mock(SingleUseObjectProvider.class);
    private VerifiedPasswordAssertions verifier;

    @BeforeEach
    void prepare() {
        when(realm.getName()).thenReturn("carenest");
        when(realm.getId()).thenReturn("realm-unit");
        Set<String> used = new HashSet<>();
        when(cache.putIfAbsent(anyString(), anyLong())).thenAnswer(call -> used.add(call.getArgument(0)));
        verifier = new VerifiedPasswordAssertions(Base64.getEncoder().encodeToString(secret), clock, cache);
    }

    private String sign(String targetRealm, String purpose, long issued, long expires) throws Exception {
        Map<String, Object> claims = Map.of(
                "userId",
                "kc-user",
                "realm",
                targetRealm,
                "purpose",
                purpose,
                "audience",
                "carenest-password-mutation",
                "issuer",
                "carenest-backend",
                "issuedAt",
                issued,
                "expiresAt",
                expires,
                "nonce",
                "n".repeat(43));
        String signed = "v1."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(JsonSerialization.writeValueAsBytes(claims));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return signed + "."
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void verifiedRecoveryAssertionIsOneUseAndBoundToTheAccount() throws Exception {
        long now = clock.instant().getEpochSecond();
        String value = sign("carenest", "RECOVERY_VERIFIED", now, now + 60);
        assertEquals("kc-user", assertDoesNotThrow(() -> verifier.verify(value, realm)));
        assertThrows(BadRequestException.class, () -> verifier.verify(value, realm));
    }

    @Test
    void rejectsTamperingAndWrongRealmWithoutConsumingNonce() throws Exception {
        long now = clock.instant().getEpochSecond();
        String value = sign("carenest", "RECOVERY_VERIFIED", now, now + 60);
        assertThrows(
                BadRequestException.class, () -> verifier.verify(value.substring(0, value.length() - 2) + "AA", realm));
        assertThrows(
                BadRequestException.class,
                () -> verifier.verify(sign("other", "RECOVERY_VERIFIED", now, now + 60), realm));
        verify(cache, never()).putIfAbsent(anyString(), anyLong());
    }

    @Test
    void rejectsExpiredFutureOverlongOrWrongPurposeAssertions() throws Exception {
        long now = clock.instant().getEpochSecond();
        for (String value : List.of(
                sign("carenest", "RECOVERY_VERIFIED", now - 60, now),
                sign("carenest", "RECOVERY_VERIFIED", now + 10, now + 70),
                sign("carenest", "RECOVERY_VERIFIED", now, now + 120),
                sign("carenest", "ADMIN_RESET", now, now + 60))) {
            assertThrows(BadRequestException.class, () -> verifier.verify(value, realm));
        }
    }
}
