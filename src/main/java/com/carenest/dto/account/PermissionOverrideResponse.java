package com.carenest.dto.account;

import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import com.carenest.utils.enums.PermissionScope;
import java.time.Instant;

public record PermissionOverrideResponse(
        Permission permission,
        PermissionEffect effect,
        PermissionScope scope,
        String reason,
        Long updatedBy,
        Instant updatedAt) {}
