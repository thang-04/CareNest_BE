package com.carenest.entity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ảnh chữ ký của một tài khoản nhân sự (SIG-01); ảnh nằm trong kho tệp theo {@code storageKey}. */
@Getter
@Entity
@Table(name = "user_signatures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "storage_key", nullable = false, length = 36)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "is_default", nullable = false)
    private boolean defaultSignature;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static UserSignature of(
            Long userId, String storageKey, String contentType, int sizeBytes, boolean defaultSignature, Instant at) {
        UserSignature signature = new UserSignature();
        signature.userId = userId;
        signature.storageKey = storageKey;
        signature.contentType = contentType;
        signature.sizeBytes = sizeBytes;
        signature.defaultSignature = defaultSignature;
        signature.createdAt = at;
        return signature;
    }

    public void changeDefault(boolean defaultSignature) {
        this.defaultSignature = defaultSignature;
    }
}
