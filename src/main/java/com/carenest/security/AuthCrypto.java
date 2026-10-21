package com.carenest.security;

import com.carenest.config.AuthProperties;
import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuthCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final AuthProperties properties;

    public AuthCrypto(AuthProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateConfiguration() {
        if (!properties.enabled()) return;
        key(properties.encryptionKey());
        key(properties.assertionSecret());
        if (!"carenest-auth-gateway".equals(properties.clientId())
                || properties.clientSecret().length() < 32
                || properties.allowedOrigins().isEmpty()) throw unavailable();
        for (String value : properties.allowedOrigins()) {
            java.net.URI origin;
            try {
                origin = java.net.URI.create(value);
            } catch (RuntimeException ex) {
                throw unavailable();
            }
            boolean local = origin.getHost() != null
                    && java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(origin.getHost());
            if (origin.getHost() == null
                    || origin.getUserInfo() != null
                    || origin.getQuery() != null
                    || origin.getFragment() != null
                    || (origin.getPath() != null && !origin.getPath().isEmpty())
                    || !("https".equals(origin.getScheme()) || local && "http".equals(origin.getScheme()))
                    || !properties.secureCookies() && !local) throw unavailable();
        }
    }

    public String seal(String value, String context) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key(properties.encryptionKey()), "AES"),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return "v1." + ENCODER.encodeToString(iv) + "."
                    + ENCODER.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw unavailable();
        }
    }

    public String open(String value, String context) {
        try {
            String[] parts = value.split("\\.");
            if (parts.length != 3 || !"v1".equals(parts[0])) throw unavailable();
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            if (iv.length != 12) throw unavailable();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key(properties.encryptionKey()), "AES"),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw unavailable();
        }
    }

    public String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public String randomOtp() {
        return String.format(java.util.Locale.ROOT, "%06d", RANDOM.nextInt(1000000));
    }

    public String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw unavailable();
        }
    }

    public String keyedHash(String value) {
        return HexFormat.of().formatHex(mac(key(properties.encryptionKey()), "lookup:" + value));
    }

    public String otpDigest(String request, String code) {
        return HexFormat.of().formatHex(mac(key(properties.encryptionKey()), "otp:" + request + ":" + code));
    }

    public boolean matchesOtp(String digest, String request, String code) {
        return digest != null
                && MessageDigest.isEqual(
                        digest.getBytes(StandardCharsets.UTF_8),
                        otpDigest(request, code).getBytes(StandardCharsets.UTF_8));
    }

    public String assertion(String subject, String realm, String purpose, Instant now) {
        if (!java.util.Set.of("RECOVERY_VERIFIED", "CURRENT_PASSWORD_VERIFIED").contains(purpose)) throw unavailable();
        byte[] json = JsonMapper.builder()
                .build()
                .writeValueAsBytes(Map.of(
                        "userId",
                        subject,
                        "realm",
                        realm,
                        "purpose",
                        purpose,
                        "audience",
                        "carenest-password-mutation",
                        "issuer",
                        "carenest-backend",
                        "issuedAt",
                        now.getEpochSecond(),
                        "expiresAt",
                        now.plusSeconds(60).getEpochSecond(),
                        "nonce",
                        randomToken()));
        String payload = "v1." + ENCODER.encodeToString(json);
        return payload + "." + ENCODER.encodeToString(mac(key(properties.assertionSecret()), payload));
    }

    private static byte[] key(String encoded) {
        try {
            byte[] key = Base64.getDecoder().decode(encoded);
            if (key.length != 32) throw unavailable();
            return key;
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
    }

    private static byte[] mac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw unavailable();
        }
    }

    private static GlobalException unavailable() {
        return new GlobalException(ApiCode.SERVICE_UNAVAILABLE, "Authentication storage configuration is unavailable");
    }
}
