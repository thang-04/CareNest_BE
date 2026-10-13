package com.carenest.utils.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * Realm role toàn cục trong Keycloak. Chỉ nói "là ai"; phạm vi campus/lớp/trẻ lấy từ DB CareNest,
 * không tạo role theo lớp/campus.
 */
public enum CareNestRole {
    ADMIN,
    PRINCIPAL,
    VICE_PRINCIPAL,
    TEACHER,
    KITCHEN_STAFF,
    PARENT;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    public static Optional<CareNestRole> fromKeycloakRole(String roleName) {
        return Arrays.stream(values())
                .filter(role -> role.name().equals(roleName))
                .findFirst();
    }

    public static Optional<CareNestRole> fromAuthority(String authority) {
        if (authority == null || !authority.startsWith(AUTHORITY_PREFIX)) {
            return Optional.empty();
        }
        return fromKeycloakRole(authority.substring(AUTHORITY_PREFIX.length()));
    }
}
