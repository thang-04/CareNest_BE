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
 * Phụ huynh trên hồ sơ trẻ, có trước khi có tài khoản (AUTH-20). {@code userId} = tài khoản đã liên kết với trẻ
 * (guardian_child_links vẫn là nguồn scope, AUTH-04); SĐT lưu dạng chuẩn hóa = tên đăng nhập phụ huynh.
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
            String email,
            Long userId) {
        ChildGuardian guardian = new ChildGuardian();
        guardian.childId = childId;
        guardian.position = (short) position;
        guardian.fullName = fullName;
        guardian.relationship = relationship;
        guardian.phone = phone;
        guardian.citizenId = citizenId;
        guardian.email = email;
        guardian.userId = userId;
        return guardian;
    }
}
