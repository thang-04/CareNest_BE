package com.carenest.service.account;

import com.carenest.entity.account.PasswordToken;
import com.carenest.utils.enums.PasswordTokenPurpose;
import java.time.Instant;

public interface AuthStateStore {

    enum Status {
        READY,
        CLAIMED,
        BUSY,
        INVALID,
        THROTTLED
    }

    record PasswordTokenState(
            String hash,
            Long userId,
            String subject,
            String lookupHash,
            String providerCipher,
            PasswordTokenPurpose purpose,
            Instant expiry,
            Instant resendAt) {
        @Override
        public String toString() {
            return "PasswordTokenState[redacted]";
        }
    }

    record Claim<T>(Status status, T data) {}

    boolean takeRate(String hash, int budget, Instant expiry);

    void savePasswordToken(PasswordToken row);

    Claim<PasswordTokenState> metadata(String hash, Instant now);

    Claim<PasswordTokenState> resend(String hash, String otpHmac, Instant now);

    Claim<PasswordTokenState> resend(String hash, String otpHmac, Instant now, String rateHash, Instant rateExpiry);

    Claim<PasswordTokenState> verifyOtp(String hash, String digest, Instant now);

    Claim<PasswordTokenState> claimPassword(String hash, String owner, Instant now);

    void finishPassword(String hash, String owner, Instant now, boolean consume);

    void revokeAccountAuth(Long id);

    void cleanup(Instant now);
}
