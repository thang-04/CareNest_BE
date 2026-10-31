package com.carenest.entity.account;

import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.PermissionScope;
import com.carenest.utils.enums.PermissionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Một dòng lịch sử phân quyền (AUTH-14); chỉ thêm, không sửa/xóa. Đích là đúng một trong role hoặc user. */
@Getter
@Entity
@Table(name = "permission_change_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermissionChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", updatable = false, length = 20)
    private CareNestRole targetRole;

    @Column(name = "target_user_id", updatable = false)
    private Long targetUserId;

    @Column(name = "permission_code", nullable = false, updatable = false, length = 64)
    private String permissionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_state", nullable = false, updatable = false, length = 10)
    private PermissionState beforeState;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_state", nullable = false, updatable = false, length = 10)
    private PermissionState afterState;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_scope", updatable = false, length = 10)
    private PermissionScope beforeScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_scope", updatable = false, length = 10)
    private PermissionScope afterScope;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public static PermissionChangeLog forRole(
            UUID batchId,
            Long actorUserId,
            CareNestRole targetRole,
            String permissionCode,
            PermissionState before,
            PermissionState after,
            String reason) {
        PermissionChangeLog log = base(batchId, actorUserId, permissionCode, before, after, reason);
        log.targetRole = targetRole;
        return log;
    }

    public static PermissionChangeLog forUser(
            UUID batchId,
            Long actorUserId,
            Long targetUserId,
            String permissionCode,
            PermissionState before,
            PermissionState after,
            String reason) {
        PermissionChangeLog log = base(batchId, actorUserId, permissionCode, before, after, reason);
        log.targetUserId = targetUserId;
        return log;
    }

    /** Phạm vi GRANT trước/sau (AUTH-20); chỉ ngoại lệ từng người có phạm vi. */
    public PermissionChangeLog withScopes(PermissionScope before, PermissionScope after) {
        this.beforeScope = before;
        this.afterScope = after;
        return this;
    }

    private static PermissionChangeLog base(
            UUID batchId,
            Long actorUserId,
            String permissionCode,
            PermissionState before,
            PermissionState after,
            String reason) {
        PermissionChangeLog log = new PermissionChangeLog();
        log.batchId = batchId;
        log.actorUserId = actorUserId;
        log.permissionCode = permissionCode;
        log.beforeState = before;
        log.afterState = after;
        log.reason = reason;
        return log;
    }
}
