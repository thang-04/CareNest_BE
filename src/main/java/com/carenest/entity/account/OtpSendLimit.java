package com.carenest.entity.account;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "otp_send_limits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtpSendLimit {
    @Id
    @Column(name = "bucket_hash", length = 64)
    private String bucketHash;

    @Column(nullable = false)
    private int used;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
