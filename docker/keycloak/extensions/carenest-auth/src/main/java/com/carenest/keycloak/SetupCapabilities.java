package com.carenest.keycloak;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;

final class SetupCapabilities {
    static final long TTL_SECONDS = 600;
    static final String GATEWAY_CLIENT = "carenest-auth-gateway";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SingleUseObjectProvider cache;
    private final RealmModel realm;
    private final Clock clock;
    private final KeycloakSession session;

    SetupCapabilities(KeycloakSession session, RealmModel realm, Clock clock) {
        this.cache = session.singleUseObjects();
        this.realm = realm;
        this.clock = clock;
        this.session = session;
    }

    String issue(UserModel user) {
        return issue(user, "FIRST_LOGIN");
    }

    String issueVerified(UserModel user) {
        return issue(user, "VERIFIED");
    }

    private String issue(UserModel user, String mode) {
        try (MutationLock ignored = lock(user.getId())) {
            return issueLocked(user, mode);
        }
    }

    private String issueLocked(UserModel user, String mode) {
        String binding = credentialBinding(user);
        if ("FIRST_LOGIN".equals(mode) && "NO_PASSWORD".equals(binding)) throw invalid();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String generation = digest(token);
        cache.put(generationKey(user), TTL_SECONDS, Map.of("generation", generation));
        cache.put(
                key(token),
                TTL_SECONDS,
                Map.of(
                        "userId",
                        user.getId(),
                        "realmId",
                        realm.getId(),
                        "clientId",
                        GATEWAY_CLIENT,
                        "generation",
                        generation,
                        "binding",
                        binding,
                        "mode",
                        mode,
                        "expiresAt",
                        Long.toString(clock.instant().getEpochSecond() + TTL_SECONDS)));
        return token;
    }

    MutationLock lock(String userId) {
        String lockKey = "carenest-password-mutation:" + realm.getId() + ":" + userId;
        if (!cache.putIfAbsent(lockKey, TTL_SECONDS)) {
            throw new ClientErrorException("Password operation is in progress", 409);
        }
        var transaction = session.getTransactionManager();
        boolean afterCompletion = transaction != null && transaction.isActive();
        if (afterCompletion) {
            transaction.enlistAfterCompletion(new AbstractKeycloakTransaction() {
                @Override
                protected void commitImpl() {
                    cache.remove(lockKey);
                }

                @Override
                protected void rollbackImpl() {
                    cache.remove(lockKey);
                }
            });
        }
        // Giữ khóa tới khi transaction Keycloak kết thúc, không chỉ tới lúc method trả về.
        return () -> {
            if (!afterCompletion) cache.remove(lockKey);
        };
    }

    interface MutationLock extends AutoCloseable {
        @Override
        void close();
    }

    Map<String, String> require(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        Map<String, String> notes = cache.get(key(token));
        if (notes == null
                || !realm.getId().equals(notes.get("realmId"))
                || !GATEWAY_CLIENT.equals(notes.get("clientId"))) throw invalid();
        try {
            if (clock.instant().getEpochSecond() >= Long.parseLong(notes.get("expiresAt"))) throw invalid();
        } catch (NumberFormatException ex) {
            throw invalid();
        }
        return notes;
    }

    void validateUser(Map<String, String> notes, UserModel user) {
        Map<String, String> generation = user == null ? null : cache.get(generationKey(user));
        if (user == null
                || !user.isEnabled()
                || !user.getId().equals(notes.get("userId"))
                || generation == null
                || !notes.get("generation").equals(generation.get("generation"))
                || !notes.get("binding").equals(credentialBinding(user))
                || !java.util.Set.of("FIRST_LOGIN", "VERIFIED").contains(notes.get("mode"))
                || "FIRST_LOGIN".equals(notes.get("mode"))
                        && !user.getRequiredActionsStream()
                                .collect(java.util.stream.Collectors.toSet())
                                .equals(java.util.Set.of("UPDATE_PASSWORD"))) throw invalid();
    }

    void consume(String token, Map<String, String> expected) {
        if (!expected.equals(cache.remove(key(token)))) throw invalid();
    }

    void invalidate(UserModel user) {
        cache.remove(generationKey(user));
    }

    private String key(String token) {
        return "carenest-setup:" + realm.getId() + ":" + digest(token);
    }

    private String generationKey(UserModel user) {
        return "carenest-setup-generation:" + realm.getId() + ":" + user.getId();
    }

    private static String credentialBinding(UserModel user) {
        return user.credentialManager()
                .getStoredCredentialsByTypeStream("password")
                .filter(c -> c.getSecretData() != null)
                .map(c -> digest(c.getId() + ":" + c.getSecretData()))
                .findFirst()
                .orElse("NO_PASSWORD");
    }

    private static String digest(String value) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static BadRequestException invalid() {
        return ProviderErrors.bad("capability_invalid", "Password capability is invalid or expired");
    }
}
