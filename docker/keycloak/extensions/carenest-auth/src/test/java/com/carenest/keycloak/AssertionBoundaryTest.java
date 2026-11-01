package com.carenest.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;

class AssertionBoundaryTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);
    private final RealmModel realm = mock(RealmModel.class);
    private final SingleUseObjectProvider cache = mock(SingleUseObjectProvider.class);
    private VerifiedPasswordAssertions verifier;

    @BeforeEach
    void prepare() {
        when(realm.getName()).thenReturn("carenest");
        when(realm.getId()).thenReturn("realm-unit");
        Set<String> used = ConcurrentHashMap.newKeySet();
        when(cache.putIfAbsent(anyString(), anyLong())).thenAnswer(call -> used.add(call.getArgument(0)));
        verifier = new VerifiedPasswordAssertions(AssertionFixture.ENCODED_SECRET, clock, cache);
    }

    private Map<String, Object> claims() {
        return AssertionFixture.claims(clock, "kc-user", "RECOVERY_VERIFIED");
    }

    @Test
    void acceptsCurrentPasswordProofAndRetainsReplayMarkerThroughClockSkew() throws Exception {
        String value = AssertionFixture.sign(AssertionFixture.claims(clock, "kc-user", "CURRENT_PASSWORD_VERIFIED"));
        assertEquals("kc-user", verifier.verify(value, realm));
        verify(cache).putIfAbsent(startsWith("carenest-password-assertion:realm-unit:"), eq(65L));
    }

    @Test
    void rejectsMalformedOrTamperedProofWithoutConsumingNonce() throws Exception {
        String value = AssertionFixture.sign(claims());
        for (String invalid : List.of(
                "",
                "userId=kc-user",
                "v2" + value.substring(2),
                "v1.!.!",
                "a".repeat(2049),
                value.substring(0, value.lastIndexOf('.') + 1) + "AA")) {
            assertThrows(BadRequestException.class, () -> verifier.verify(invalid, realm));
        }
        assertThrows(BadRequestException.class, () -> verifier.verify(null, realm));
        verify(cache, never()).putIfAbsent(anyString(), anyLong());
        assertEquals("kc-user", verifier.verify(value, realm));
    }

    @Test
    void rejectsEveryInvalidSignedBindingBeforeConsumingNonce() throws Exception {
        long now = clock.instant().getEpochSecond();
        for (var invalid : List.of(
                Map.entry("realm", "other"),
                Map.entry("issuer", "other"),
                Map.entry("audience", "other"),
                Map.entry("purpose", "ADMIN_RESET"),
                Map.entry("userId", ""),
                Map.entry("userId", "../kc-user"),
                Map.entry("userId", "x".repeat(37)),
                Map.entry("nonce", "short"),
                Map.entry("nonce", "!".repeat(43)),
                Map.entry("issuedAt", now + 6),
                Map.entry("issuedAt", now - 61),
                Map.entry("expiresAt", now),
                Map.entry("expiresAt", now + 61))) {
            var body = claims();
            body.put(invalid.getKey(), invalid.getValue());
            String value = AssertionFixture.sign(body);
            assertThrows(BadRequestException.class, () -> verifier.verify(value, realm), invalid.getKey());
        }
        for (String field :
                List.of("realm", "issuer", "audience", "purpose", "userId", "nonce", "issuedAt", "expiresAt")) {
            var body = claims();
            body.remove(field);
            String value = AssertionFixture.sign(body);
            assertThrows(BadRequestException.class, () -> verifier.verify(value, realm), field);
        }
        verify(cache, never()).putIfAbsent(anyString(), anyLong());
    }

    @Test
    void rejectsEmptyValidityWindowEvenWithPermittedClockSkew() throws Exception {
        var body = claims();
        long future = clock.instant().getEpochSecond() + 5;
        body.put("issuedAt", future);
        body.put("expiresAt", future);
        String value = AssertionFixture.sign(body);
        assertThrows(BadRequestException.class, () -> verifier.verify(value, realm));
        verify(cache, never()).putIfAbsent(anyString(), anyLong());
    }

    @Test
    void assertionNonceCannotBeReusedWithAnotherSignedSubjectOrPurpose() throws Exception {
        assertEquals("kc-user", verifier.verify(AssertionFixture.sign(claims()), realm));
        var other = AssertionFixture.claims(clock, "other-user", "CURRENT_PASSWORD_VERIFIED");
        String value = AssertionFixture.sign(other);
        assertThrows(BadRequestException.class, () -> verifier.verify(value, realm));
    }

    @Test
    void concurrentRedemptionHasExactlyOneWinner() throws Exception {
        String value = AssertionFixture.sign(claims());
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Redemption barrier timed out");
                        try {
                            verifier.verify(value, realm);
                            return 1;
                        } catch (BadRequestException expected) {
                            return 0;
                        }
                    }))
                    .toList();
            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allReady);
            int accepted = 0;
            for (var future : futures) accepted += future.get(5, TimeUnit.SECONDS);
            assertEquals(1, accepted);
        }
    }

    @Test
    void invalidSecretConfigurationFailsClosed() {
        for (String invalid : List.of(
                "",
                "not-base64!",
                Base64.getEncoder().encodeToString(new byte[31]),
                Base64.getEncoder().encodeToString(new byte[33]))) {
            assertThrows(
                    ServiceUnavailableException.class, () -> new VerifiedPasswordAssertions(invalid, clock, cache));
        }
        assertThrows(ServiceUnavailableException.class, () -> new VerifiedPasswordAssertions(null, clock, cache));
    }
}
