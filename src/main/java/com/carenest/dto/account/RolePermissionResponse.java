package com.carenest.dto.account;

import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;

/**
 * Hàng ma trận của một role. editable = HT sửa được (chỉ VICE_PRINCIPAL/TEACHER/KITCHEN_STAFF);
 * configurablePermissions = các ô được bật/tắt; permissions = quyền role đang có.
 */
public record RolePermissionResponse(
        CareNestRole role, boolean editable, List<Permission> configurablePermissions, List<Permission> permissions) {}
