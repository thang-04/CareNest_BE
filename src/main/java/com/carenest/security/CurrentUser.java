package com.carenest.security;

import java.util.Set;

/**
 * Người dùng đang gọi API: hồ sơ lấy từ {@code user_accounts} theo JWT {@code sub}, role lấy từ token.
 * Controller nhận tham số kiểu này (CurrentUserArgumentResolver) rồi truyền xuống service.
 */
public record CurrentUser(
        Long userId, String keycloakUserId, String username, String fullName, String email, Set<CareNestRole> roles) {

    public CurrentUser {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(CareNestRole role) {
        return roles.contains(role);
    }
}
