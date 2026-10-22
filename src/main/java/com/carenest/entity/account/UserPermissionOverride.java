package com.carenest.entity.account;

import com.carenest.utils.enums.PermissionEffect;
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

/** Ngoại lệ quyền của một người (GRANT/REVOKE) so với ma trận role của họ. */
@Getter
@Entity
@Table(name = "user_permission_overrides")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPermissionOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "permission_code", nullable = false, updatable = false, length = 64)
    private String permissionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PermissionEffect effect;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserPermissionOverride create(
            Long userId, String permissionCode, PermissionEffect effect, String reason, Long createdBy) {
        UserPermissionOverride override = new UserPermissionOverride();
        override.userId = userId;
        override.permissionCode = permissionCode;
        override.effect = effect;
        override.reason = reason;
        override.createdBy = createdBy;
        return override;
    }

    public void change(PermissionEffect newEffect, String newReason, Long changedBy) {
        this.effect = newEffect;
        this.reason = newReason;
        this.createdBy = changedBy;
    }
}
