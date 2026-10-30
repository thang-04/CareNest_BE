package com.carenest.security.authorization;

import com.carenest.entity.account.RolePermission;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Quyền hiệu lực (AUTH-10, AUTH-13) = phần cố định của role ∪ (ma trận role cấu hình ∪ GRANT − REVOKE).
 * REVOKE chỉ trừ phần cấu hình, không trừ phần cố định; GRANT/ô ma trận chỉ nhận khi quyền cấu hình được cho
 * một role cấu hình của user — dòng DB lệch (role đổi trên Keycloak, code bị bỏ) bị bỏ qua thay vì mở quyền.
 */
public final class EffectivePermissionResolver {

    private EffectivePermissionResolver() {}

    public static Set<Permission> resolve(
            Set<CareNestRole> roles,
            Collection<RolePermission> matrixRows,
            Collection<UserPermissionOverride> overrides) {
        EnumSet<Permission> fixed = EnumSet.noneOf(Permission.class);
        for (CareNestRole role : roles) {
            fixed.addAll(Permission.fixedPermissionsOf(role));
        }

        EnumSet<Permission> configured = EnumSet.noneOf(Permission.class);
        for (RolePermission row : matrixRows) {
            Optional<Permission> permission = Permission.fromCode(row.getPermissionCode());
            if (roles.contains(row.getRole())
                    && permission.isPresent()
                    && permission.get().isConfigurableFor(row.getRole())) {
                configured.add(permission.get());
            }
        }
        for (UserPermissionOverride override : overrides) {
            Optional<Permission> permission = Permission.fromCode(override.getPermissionCode());
            if (permission.isEmpty() || !isConfigurableForAny(permission.get(), roles)) {
                continue;
            }
            if (override.getEffect() == PermissionEffect.GRANT) {
                configured.add(permission.get());
            } else {
                configured.remove(permission.get());
            }
        }

        fixed.addAll(configured);
        return fixed;
    }

    public static boolean isConfigurableForAny(Permission permission, Set<CareNestRole> roles) {
        return roles.stream().anyMatch(permission::isConfigurableFor);
    }
}
