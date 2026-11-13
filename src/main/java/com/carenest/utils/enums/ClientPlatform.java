package com.carenest.utils.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Nền tảng gọi API, suy từ claim {@code azp} (client Keycloak đã xin token, nằm trong chữ ký) chứ không từ header
 * client tự khai. Mỗi nền tảng chỉ giữ role được dùng ở đó (AUTH-21).
 */
public enum ClientPlatform {
    WEB(
            EnumSet.of(CareNestRole.ADMIN, CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER),
            EnumSet.noneOf(CareNestRole.class)),
    // ADR-0026: BGH dùng App chỉ xem — lệnh ghi bỏ role BGH (MobileStaffReadOnlyFilter)
    MOBILE(
            EnumSet.of(
                    CareNestRole.TEACHER,
                    CareNestRole.KITCHEN_STAFF,
                    CareNestRole.PARENT,
                    CareNestRole.PRINCIPAL,
                    CareNestRole.VICE_PRINCIPAL),
            EnumSet.of(CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL)),
    // Client chỉ dùng ở môi trường dev (lấy token bằng curl), giữ mọi role
    DEV(EnumSet.allOf(CareNestRole.class), EnumSet.noneOf(CareNestRole.class));

    /** Token không còn role nào dùng được trên nền tảng của nó; SecurityConfig từ chối mọi request mang authority này. */
    public static final String DENIED_AUTHORITY = "PLATFORM_DENIED";

    private final Set<CareNestRole> allowedRoles;
    private final Set<CareNestRole> readOnlyRoles;

    ClientPlatform(Set<CareNestRole> allowedRoles, Set<CareNestRole> readOnlyRoles) {
        this.allowedRoles = Set.copyOf(allowedRoles);
        this.readOnlyRoles = Set.copyOf(readOnlyRoles);
    }

    public Set<CareNestRole> allowedRoles() {
        return allowedRoles;
    }

    /** Role giữ trên nền tảng nhưng chỉ được đọc; luôn là tập con của {@link #allowedRoles()}. */
    public Set<CareNestRole> readOnlyRoles() {
        return readOnlyRoles;
    }
}
