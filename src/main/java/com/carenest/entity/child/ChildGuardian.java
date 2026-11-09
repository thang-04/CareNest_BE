package com.carenest.entity.child;

import com.carenest.utils.enums.GuardianRelationship;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Phụ huynh trên hồ sơ trẻ, có trước khi có tài khoản (AUTH-20). {@code userId} = tài khoản đã liên kết với trẻ, là
 * nguồn scope của phụ huynh (AUTH-04); SĐT lưu dạng chuẩn hóa = tên đăng nhập phụ huynh.
 */
@Getter
@Entity
@Table(name = "child_guardians")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildGuardian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false, updatable = false)
    private Long childId;

    @Column(nullable = false)
    private short position;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GuardianRelationship relationship;

    @Column(nullable = false, length = 10)
    private String phone;

    @Column(name = "citizen_id", length = 12)
    private String citizenId;

    @Column(length = 255)
    private String email;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "account_recipient", nullable = false)
    private boolean accountRecipient;

    public void selectAccountRecipient(boolean selected) {
        this.accountRecipient = selected;
    }

    /** Liên kết tài khoản với trẻ; đã liên kết đúng tài khoản này thì giữ thời điểm cũ. */
    public void linkAccount(Long accountId, Instant at) {
        if (!accountId.equals(this.userId)) {
            this.userId = accountId;
            this.linkedAt = at;
        }
    }

    /** Gỡ liên kết tài khoản khỏi trẻ (AUTH-19); tài khoản phụ huynh giữ nguyên. */
    public void unlinkAccount() {
        this.userId = null;
        this.linkedAt = null;
    }

    @Column(name = "photo_key", length = 36)
    private String photoKey;

    @Column(name = "photo_content_type", length = 30)
    private String photoContentType;

    @Column(name = "photo_size_bytes")
    private Integer photoSizeBytes;

    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    public void assignEnrollmentPhoto(String key, String contentType, int size, Instant at) {
        this.photoKey = key;
        this.photoContentType = contentType;
        this.photoSizeBytes = size;
        this.photoUpdatedAt = at;
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChildGuardian create(
            Long childId,
            int position,
            String fullName,
            GuardianRelationship relationship,
            String phone,
            String citizenId,
            String email) {
        ChildGuardian guardian = new ChildGuardian();
        guardian.childId = childId;
        guardian.position = (short) position;
        guardian.fullName = fullName;
        guardian.relationship = relationship;
        guardian.phone = phone;
        guardian.citizenId = citizenId;
        guardian.email = email;
        return guardian;
    }
}
