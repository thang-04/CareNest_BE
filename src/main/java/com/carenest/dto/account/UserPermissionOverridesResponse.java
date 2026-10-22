package com.carenest.dto.account;

import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;

/**
 * Ngoại lệ quyền của một người. configurablePermissions = quyền được tạo ngoại lệ theo role của họ;
 * effectivePermissions = quyền thực tế sau khi áp ma trận + ngoại lệ.
 */
public record UserPermissionOverridesResponse(
        Long userId,
        List<CareNestRole> roles,
        List<Permission> configurablePermissions,
        List<PermissionOverrideResponse> overrides,
        List<Permission> effectivePermissions) {}
