package com.carenest.mapper.account;

import com.carenest.dto.account.PermissionChangeLogResponse;
import com.carenest.dto.account.PermissionOverrideResponse;
import com.carenest.dto.account.PermissionResponse;
import com.carenest.entity.account.PermissionChangeLog;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.utils.enums.Permission;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class PermissionMapper {

    public PermissionResponse toPermissionResponse(Permission permission) {
        return new PermissionResponse(
                permission,
                permission.module(),
                permission.description(),
                permission.defaultRoles().stream().sorted().toList(),
                permission.configurableRoles().stream().sorted().toList(),
                permission.campusScoped());
    }

    public PermissionOverrideResponse toOverrideResponse(Permission permission, UserPermissionOverride override) {
        return new PermissionOverrideResponse(
                permission,
                override.getEffect(),
                override.getScope(),
                override.getCampusId(),
                override.getReason(),
                override.getCreatedBy(),
                override.getUpdatedAt());
    }

    /** nameOf: userId → họ tên (null nếu không còn tài khoản) để màn lịch sử không phải hiện id. */
    public PermissionChangeLogResponse toChangeLogResponse(PermissionChangeLog log, Function<Long, String> nameOf) {
        return new PermissionChangeLogResponse(
                log.getId(),
                log.getBatchId(),
                log.getActorUserId(),
                nameOf.apply(log.getActorUserId()),
                log.getTargetRole(),
                log.getTargetUserId(),
                log.getTargetUserId() == null ? null : nameOf.apply(log.getTargetUserId()),
                log.getPermissionCode(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getBeforeScope(),
                log.getAfterScope(),
                log.getBeforeCampusId(),
                log.getAfterCampusId(),
                log.getReason(),
                log.getChangedAt());
    }
}
