package com.carenest.keycloak;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.util.JsonSerialization;

final class VerifiedPasswordAssertions {
    private final byte[] secret;
    private final Clock clock;
    private final SingleUseObjectProvider cache;

    VerifiedPasswordAssertions(String secret, Clock clock, SingleUseObjectProvider cache) {
        try {
            this.secret = Base64.getDecoder().decode(secret); // carenest:allow-secret
        } catch (RuntimeException ex) {
            throw new ServiceUnavailableException("Password authorization is not configured");
        }
        if (this.secret.length != 32) throw new ServiceUnavailableException("Password authorization is not configured");
        this.clock = clock;
        this.cache = cache;
    }

    String verify(String assertion, RealmModel realm) {
        try {
            if (assertion == null || assertion.length() > 2048) throw invalid();
            String[] parts = assertion.split("\\.");
            if (parts.length != 3 || !"v1".equals(parts[0])) throw invalid();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(
                    mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8)), signature))
                throw invalid();
            Claims claims = JsonSerialization.readValue(Base64.getUrlDecoder().decode(parts[1]), Claims.class);
            long now = clock.instant().getEpochSecond();
            if (!realm.getName().equals(claims.realm())
                    || !"carenest-backend".equals(claims.issuer())
                    || !"carenest-password-mutation".equals(claims.audience())
                    || claims.purpose() == null
                    || !Set.of("RECOVERY_VERIFIED", "CURRENT_PASSWORD_VERIFIED").contains(claims.purpose())
                    || claims.userId() == null
                    || !claims.userId().matches("[A-Za-z0-9_-]{1,36}")
                    || claims.nonce() == null
                    || !claims.nonce().matches("[A-Za-z0-9_-]{43}")
                    || claims.issuedAt() > now + 5
                    || claims.issuedAt() < now - 60
                    || claims.expiresAt() <= now
                    || claims.expiresAt() <= claims.issuedAt()
                    || claims.expiresAt() > claims.issuedAt() + 60) throw invalid();
            String key = "carenest-password-assertion:" + realm.getId() + ":"
                    + Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(MessageDigest.getInstance("SHA-256")
                                    .digest(claims.nonce().getBytes(StandardCharsets.UTF_8)));
            if (!cache.putIfAbsent(key, Math.max(1, claims.expiresAt() - now + 5))) throw invalid();
            return claims.userId();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static BadRequestException invalid() {
        return ProviderErrors.bad("assertion_invalid", "Invalid password authorization");
    }

    record Claims(
            String userId,
            String realm,
            String purpose,
            String audience,
            String issuer,
            long issuedAt,
            long expiresAt,
            String nonce) {
        @Override
        public String toString() {
            return "Claims[redacted]";
        }
    }
}
