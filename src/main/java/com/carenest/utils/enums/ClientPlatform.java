package com.carenest.utils.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Nền tảng gọi API, suy từ claim {@code azp} (client Keycloak đã xin token, nằm trong chữ ký) chứ không từ header
 * client tự khai. Mỗi nền tảng chỉ giữ role được dùng ở đó (AUTH-21).
 */
public enum ClientPlatform {
    WEB(EnumSet.of(CareNestRole.ADMIN, CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER)),
    MOBILE(EnumSet.of(CareNestRole.TEACHER, CareNestRole.KITCHEN_STAFF, CareNestRole.PARENT)),
    // Client chỉ dùng ở môi trường dev (lấy token bằng curl), giữ mọi role
    DEV(EnumSet.allOf(CareNestRole.class));

    /** Token không còn role nào dùng được trên nền tảng của nó; SecurityConfig từ chối mọi request mang authority này. */
    public static final String DENIED_AUTHORITY = "PLATFORM_DENIED";

    private final Set<CareNestRole> allowedRoles;

    ClientPlatform(Set<CareNestRole> allowedRoles) {
        this.allowedRoles = Set.copyOf(allowedRoles);
    }

    public Set<CareNestRole> allowedRoles() {
        return allowedRoles;
    }
}
