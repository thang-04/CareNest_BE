package com.carenest.dto.account;

import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import java.time.Instant;

public record PermissionOverrideResponse(
        Permission permission, PermissionEffect effect, String reason, Long updatedBy, Instant updatedAt) {}
