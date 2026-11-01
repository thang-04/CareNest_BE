package com.carenest.security;

import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Quyền mặc định của role (= seed V7 + phần cố định), để dựng CurrentUser trong test. */
public final class TestPermissions {

    private TestPermissions() {}

    public static Set<Permission> defaultsOf(CareNestRole... roles) {
        Set<CareNestRole> roleSet = Set.of(roles);
        return Arrays.stream(Permission.values())
                .filter(permission -> permission.defaultRoles().stream().anyMatch(roleSet::contains))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
    }

    public static CurrentUser userWithDefaults(Long userId, CareNestRole... roles) {
        return new CurrentUser(userId, "kc-" + userId, "demo.user", "Demo", null, Set.of(roles), defaultsOf(roles));
    }
}
