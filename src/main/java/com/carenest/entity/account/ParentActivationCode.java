package com.carenest.entity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Mã kích hoạt hiện hành của một tài khoản phụ huynh (AUTH-18, ADR-0024). Chỉ lưu hash, không lưu mã gốc. */
@Getter
@Entity
@Table(name = "parent_activation_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParentActivationCode {
    public static final int MAX_FAILED_ATTEMPTS = 5;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public static ParentActivationCode issue(Long userId, String codeHash, Instant sentAt, Instant expiresAt) {
        ParentActivationCode row = new ParentActivationCode();
        row.userId = userId;
        row.reissue(codeHash, sentAt, expiresAt);
        return row;
    }

    /** Gửi lại: mã mới thay mã cũ, đếm lần sai lại từ đầu. */
    public void reissue(String codeHash, Instant sentAt, Instant expiresAt) {
        this.codeHash = codeHash;
        this.sentAt = sentAt;
        this.expiresAt = expiresAt;
        this.failedAttempts = 0;
    }

    public boolean usable(Instant now) {
        return activatedAt == null && failedAttempts < MAX_FAILED_ATTEMPTS && now.isBefore(expiresAt);
    }

    public void failAttempt() {
        failedAttempts = (short) Math.min(MAX_FAILED_ATTEMPTS, failedAttempts + 1);
    }

    public void markActivated(Instant now) {
        activatedAt = now;
    }
}
