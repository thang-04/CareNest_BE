package com.carenest.dto.account;

import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;

/** Một quyền trong danh mục: role mặc định có quyền và role HT được bật/tắt. */
public record PermissionResponse(
        Permission code,
        String module,
        String description,
        List<CareNestRole> defaultRoles,
        List<CareNestRole> configurableRoles) {}
