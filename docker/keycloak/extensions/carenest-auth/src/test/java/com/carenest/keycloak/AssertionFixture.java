package com.carenest.keycloak;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.keycloak.util.JsonSerialization;

final class AssertionFixture {
    static final byte[] SECRET = new byte[32];
    static final String ENCODED_SECRET = Base64.getEncoder().encodeToString(SECRET); // carenest:allow-secret

    static Map<String, Object> claims(Clock clock, String userId, String purpose) {
        long now = clock.instant().getEpochSecond();
        return new HashMap<>(Map.of(
                "userId",
                userId,
                "realm",
                "carenest",
                "purpose",
                purpose,
                "audience",
                "carenest-password-mutation",
                "issuer",
                "carenest-backend",
                "issuedAt",
                now,
                "expiresAt",
                now + 60,
                "nonce",
                "n".repeat(43)));
    }

    static String sign(Map<String, Object> claims) throws Exception {
        String signed = "v1."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(JsonSerialization.writeValueAsBytes(claims));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        return signed + "."
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
    }
}
