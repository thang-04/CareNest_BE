package com.carenest.entity.account;

import com.carenest.utils.enums.PasswordTokenPurpose;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "password_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordToken {
    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PasswordTokenPurpose purpose;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "keycloak_user_id", length = 36)
    private String keycloakUserId;

    @Column(name = "lookup_hash", length = 64)
    private String lookupHash;

    @Column(name = "provider_cipher", columnDefinition = "text")
    private String providerCipher;

    @Column(name = "otp_hmac", length = 64)
    private String otpHmac;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_available_at")
    private Instant resendAvailableAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "processing_owner", length = 43)
    private String processingOwner;

    public static PasswordToken create(
            String hash,
            PasswordTokenPurpose purpose,
            Long user,
            String subject,
            String lookup,
            String provider,
            String otp,
            Instant created,
            Instant expiry,
            Instant resend) {
        PasswordToken row = new PasswordToken();
        row.tokenHash = hash;
        row.purpose = purpose;
        row.userId = user;
        row.keycloakUserId = subject;
        row.lookupHash = lookup;
        row.providerCipher = provider;
        row.otpHmac = otp;
        row.createdAt = created;
        row.expiresAt = expiry;
        row.resendAvailableAt = resend;
        return row;
    }

    public void failAttempt() {
        attempts = Math.min(5, attempts + 1);
    }

    public void consume(Instant now) {
        consumedAt = now;
        processingOwner = null;
    }

    public void claim(String owner) {
        processingOwner = owner;
    }

    public void release() {
        processingOwner = null;
    }

    public void resend(String otp, Instant expiry, Instant next) {
        otpHmac = otp;
        expiresAt = expiry;
        resendAvailableAt = next;
    }
}
