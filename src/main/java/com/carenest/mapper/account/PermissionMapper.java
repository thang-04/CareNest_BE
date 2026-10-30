package com.carenest.mapper.account;

import com.carenest.dto.account.PermissionChangeLogResponse;
import com.carenest.dto.account.PermissionOverrideResponse;
import com.carenest.dto.account.PermissionResponse;
import com.carenest.entity.account.PermissionChangeLog;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.utils.enums.Permission;
import org.springframework.stereotype.Component;

@Component
public class PermissionMapper {

    public PermissionResponse toPermissionResponse(Permission permission) {
        return new PermissionResponse(
                permission,
                permission.module(),
                permission.description(),
                permission.defaultRoles().stream().sorted().toList(),
                permission.configurableRoles().stream().sorted().toList());
    }

    public PermissionOverrideResponse toOverrideResponse(Permission permission, UserPermissionOverride override) {
        return new PermissionOverrideResponse(
                permission,
                override.getEffect(),
                override.getReason(),
                override.getCreatedBy(),
                override.getUpdatedAt());
    }

    public PermissionChangeLogResponse toChangeLogResponse(PermissionChangeLog log) {
        return new PermissionChangeLogResponse(
                log.getId(),
                log.getBatchId(),
                log.getActorUserId(),
                log.getTargetRole(),
                log.getTargetUserId(),
                log.getPermissionCode(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getReason(),
                log.getChangedAt());
    }
}
