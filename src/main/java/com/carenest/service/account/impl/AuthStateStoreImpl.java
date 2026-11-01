package com.carenest.service.account.impl;

import com.carenest.entity.account.PasswordToken;
import com.carenest.repository.account.*;
import com.carenest.service.account.AuthStateStore;
import com.carenest.utils.enums.PasswordTokenPurpose;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthStateStoreImpl implements AuthStateStore {
    private final PasswordTokenRepository passwordTokens;
    private final OtpSendLimitRepository otpSendLimits;
    private final UserAccountRepository accounts;

    @Override
    @Transactional
    public boolean takeRate(String hash, int budget, Instant expiry) {
        return otpSendLimits.take(hash, expiry, budget) == 1;
    }

    @Override
    @Transactional
    public void savePasswordToken(PasswordToken row) {
        passwordTokens.save(row);
    }

    @Override
    @Transactional(readOnly = true)
    public Claim<PasswordTokenState> metadata(String hash, Instant now) {
        PasswordToken row = passwordTokens.findById(hash).orElse(null);
        return otpRequestOpen(row, now) ? new Claim<>(Status.READY, snapshot(row)) : new Claim<>(Status.INVALID, null);
    }

    @Override
    @Transactional
    public Claim<PasswordTokenState> resend(String hash, String otpHmac, Instant now) {
        return resend(hash, otpHmac, now, null, null);
    }

    @Override
    @Transactional
    public Claim<PasswordTokenState> resend(
            String hash, String otpHmac, Instant now, String rateHash, Instant rateExpiry) {
        PasswordToken row = passwordTokens.findLocked(hash).orElse(null);
        if (!otpRequestOpen(row, now)) return new Claim<>(Status.INVALID, null);
        if (row.getResendAvailableAt().isAfter(now)) return new Claim<>(Status.THROTTLED, snapshot(row));
        boolean allowed = rateHash == null || otpSendLimits.take(rateHash, rateExpiry, 5) == 1;
        row.resend(otpHmac, now.plusSeconds(300), now.plusSeconds(60));
        if (!allowed)
            return new Claim<>(
                    Status.READY,
                    new PasswordTokenState(
                            row.getTokenHash(),
                            null,
                            null,
                            row.getLookupHash(),
                            null,
                            row.getPurpose(),
                            row.getExpiresAt(),
                            row.getResendAvailableAt()));
        return new Claim<>(Status.READY, snapshot(row));
    }

    @Override
    @Transactional
    public Claim<PasswordTokenState> verifyOtp(String hash, String digest, Instant now) {
        PasswordToken row = passwordTokens.findLocked(hash).orElse(null);
        if (!otpRequestOpen(row, now) || !row.getExpiresAt().isAfter(now)) return new Claim<>(Status.INVALID, null);
        if (row.getAttempts() >= 5) return new Claim<>(Status.THROTTLED, null);
        boolean matches = java.security.MessageDigest.isEqual(
                row.getOtpHmac().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                digest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!matches || row.getUserId() == null || !active(row.getUserId())) {
            row.failAttempt();
            // Trả trạng thái để transaction commit bộ đếm; exception chỉ ném ở service gọi bên ngoài.
            return new Claim<>(row.getAttempts() >= 5 ? Status.THROTTLED : Status.INVALID, null);
        }
        row.consume(now);
        return new Claim<>(Status.READY, snapshot(row));
    }

    @Override
    @Transactional
    public Claim<PasswordTokenState> claimPassword(String hash, String owner, Instant now) {
        PasswordToken row = passwordTokens.findLocked(hash).orElse(null);
        if (row == null
                || row.getPurpose() == PasswordTokenPurpose.OTP
                || row.getConsumedAt() != null
                || !row.getExpiresAt().isAfter(now)
                || !active(row.getUserId())) return new Claim<>(Status.INVALID, null);
        if (row.getProcessingOwner() != null) return new Claim<>(Status.BUSY, null);
        row.claim(owner);
        return new Claim<>(Status.CLAIMED, snapshot(row));
    }

    @Override
    @Transactional
    public void finishPassword(String hash, String owner, Instant now, boolean consume) {
        passwordTokens
                .findLocked(hash)
                .filter(row -> owner.equals(row.getProcessingOwner()))
                .ifPresent(row -> {
                    if (consume) row.consume(now);
                    else row.release();
                });
    }

    @Override
    @Transactional
    public void revokeAccountAuth(Long id) {
        passwordTokens.deleteByUserId(id);
    }

    @Override
    @Transactional
    public void cleanup(Instant now) {
        passwordTokens.deleteExpired(now.minusSeconds(3600));
        otpSendLimits.deleteExpired(now);
    }

    private boolean active(Long id) {
        return id != null
                && accounts.findById(id)
                        .map(com.carenest.entity.account.UserAccount::isActive)
                        .orElse(false);
    }

    private static boolean otpRequestOpen(PasswordToken row, Instant now) {
        return row != null
                && row.getPurpose() == PasswordTokenPurpose.OTP
                && row.getConsumedAt() == null
                && row.getCreatedAt().plusSeconds(1800).isAfter(now);
    }

    private static PasswordTokenState snapshot(PasswordToken row) {
        return new PasswordTokenState(
                row.getTokenHash(),
                row.getUserId(),
                row.getKeycloakUserId(),
                row.getLookupHash(),
                row.getProviderCipher(),
                row.getPurpose(),
                row.getExpiresAt(),
                row.getResendAvailableAt());
    }
}
