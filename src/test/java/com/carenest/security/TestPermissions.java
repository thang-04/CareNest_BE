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

    /** Như defaultsOf; GV cộng thêm quyền mặc định cột Tổ trưởng (AUTH-23) để test kế hoạch giáo dục giữ hành vi. */
    public static Set<Permission> eduDefaultsOf(CareNestRole... roles) {
        Set<Permission> result = defaultsOf(roles);
        if (Set.of(roles).contains(CareNestRole.TEACHER)) result.addAll(defaultsOf(CareNestRole.TEAM_LEADER));
        return result;
    }

    public static CurrentUser userWithDefaults(Long userId, CareNestRole... roles) {
        return new CurrentUser(userId, "kc-" + userId, "demo.user", "Demo", null, Set.of(roles), defaultsOf(roles));
    }
}
