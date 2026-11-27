package com.carenest.dto.account;

import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.PermissionScope;
import com.carenest.utils.enums.PermissionState;
import java.time.Instant;
import java.util.UUID;

/** Một dòng lịch sử phân quyền; đích là targetRole hoặc targetUserId. permissionCode giữ chuỗi gốc đã lưu. */
public record PermissionChangeLogResponse(
        Long id,
        UUID batchId,
        Long actorUserId,
        String actorName,
        CareNestRole targetRole,
        Long targetUserId,
        String targetUserName,
        String permissionCode,
        PermissionState beforeState,
        PermissionState afterState,
        PermissionScope beforeScope,
        PermissionScope afterScope,
        Long beforeCampusId,
        Long afterCampusId,
        String reason,
        Instant changedAt) {}
