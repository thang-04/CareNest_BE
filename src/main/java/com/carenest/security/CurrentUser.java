package com.carenest.security;

import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.Set;

/**
 * Người dùng đang gọi API: hồ sơ lấy từ {@code user_accounts} theo JWT {@code sub}, role lấy từ token, quyền hiệu lực
 * tính từ role + ma trận + ngoại lệ trong DB (ADR-0017). Controller nhận tham số kiểu này (CurrentUserArgumentResolver)
 * rồi truyền xuống service.
 */
public record CurrentUser(
        Long userId,
        String keycloakUserId,
        String username,
        String fullName,
        String email,
        Set<CareNestRole> roles,
        Set<Permission> permissions) {

    public CurrentUser {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    /** Không mang quyền nào; dùng cho chỗ chỉ cần danh tính/role (service, policy). */
    public CurrentUser(
            Long userId,
            String keycloakUserId,
            String username,
            String fullName,
            String email,
            Set<CareNestRole> roles) {
        this(userId, keycloakUserId, username, fullName, email, roles, Set.of());
    }

    public boolean hasRole(CareNestRole role) {
        return roles.contains(role);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
