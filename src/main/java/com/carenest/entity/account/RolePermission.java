package com.carenest.entity.account;

import com.carenest.utils.enums.CareNestRole;
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
import org.hibernate.annotations.UpdateTimestamp;

/** Một ô "bật" của ma trận quyền theo role. Mã quyền lưu chuỗi để code bị bỏ khỏi enum không làm vỡ việc đọc. */
@Getter
@Entity
@Table(name = "role_permissions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private CareNestRole role;

    @Column(name = "permission_code", nullable = false, updatable = false, length = 64)
    private String permissionCode;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static RolePermission create(CareNestRole role, String permissionCode, Long updatedBy) {
        RolePermission rolePermission = new RolePermission();
        rolePermission.role = role;
        rolePermission.permissionCode = permissionCode;
        rolePermission.updatedBy = updatedBy;
        return rolePermission;
    }
}
