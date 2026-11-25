package com.carenest.security.authorization;

import com.carenest.entity.account.RolePermission;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import com.carenest.utils.enums.PermissionScope;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
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
                    && permission.get().inRoleMatrix()
                    && permission.get().isConfigurableFor(row.getRole())) {
                configured.add(permission.get());
            }
        }
        for (UserPermissionOverride override : overrides) {
            Optional<Permission> permission = Permission.fromCode(override.getPermissionCode());
            // EDU-110 (BR5.1): 8 mã soạn/duyệt không có ngoại lệ theo người; dòng cũ bị bỏ qua
            if (permission.isEmpty()
                    || !permission.get().overridable()
                    || !isConfigurableForAny(permission.get(), roles)) {
                continue;
            }
            if (override.getEffect() == PermissionEffect.GRANT) {
                configured.add(permission.get());
            } else {
                configured.remove(permission.get());
            }
        }
        // Tiền 24/11/2026: PHT bị REVOKE "Phụ trách chuyên môn" (PHT dinh dưỡng) chỉ xem kế hoạch giáo dục đã ban hành
        // PHT mặc định chuyên môn (EDU-108: mặc định bật) nên mã có sẵn, không cần dòng ma trận
        configured.remove(Permission.EDU_ACADEMIC_DUTY);
        if (roles.contains(CareNestRole.VICE_PRINCIPAL)) {
            if (academicDutyRevoked(overrides)) configured.removeAll(Permission.EDU_ACADEMIC_ONLY);
            else configured.add(Permission.EDU_ACADEMIC_DUTY);
        }

        fixed.addAll(configured);
        return fixed;
    }

    /**
     * Mã sửa theo mảng được GRANT phạm vi cả trường (AUTH-20). Ô ma trận và GRANT phạm vi CAMPUS ⇒ chỉ điểm trường
     * phân công. Dòng lệch (mã bỏ, mã không theo mảng, không cấu hình được cho role) bị bỏ qua như {@link #resolve}.
     */
    public static Set<Permission> schoolWide(Set<CareNestRole> roles, Collection<UserPermissionOverride> overrides) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (UserPermissionOverride override : overrides) {
            Optional<Permission> permission = Permission.fromCode(override.getPermissionCode());
            if (override.getEffect() == PermissionEffect.GRANT
                    && override.getScope() == PermissionScope.SCHOOL
                    && permission.isPresent()
                    && permission.get().campusScoped()
                    && isConfigurableForAny(permission.get(), roles)) {
                result.add(permission.get());
            }
        }
        return result;
    }

    /**
     * Mã sửa theo mảng được GRANT tại một điểm trường cụ thể (AUTH-20, chốt 21/11/2026): mã → điểm trường. PHT chỉ sửa
     * việc đó ở điểm trường này, không kèm điểm trường đang phụ trách. Dòng lệch bị bỏ qua như {@link #schoolWide}.
     */
    public static Map<Permission, Long> pinnedCampuses(
            Set<CareNestRole> roles, Collection<UserPermissionOverride> overrides) {
        Map<Permission, Long> result = new EnumMap<>(Permission.class);
        for (UserPermissionOverride override : overrides) {
            Optional<Permission> permission = Permission.fromCode(override.getPermissionCode());
            if (override.getEffect() == PermissionEffect.GRANT
                    && override.getScope() == PermissionScope.CAMPUS
                    && override.getCampusId() != null
                    && permission.isPresent()
                    && permission.get().campusScoped()
                    && isConfigurableForAny(permission.get(), roles)) {
                result.put(permission.get(), override.getCampusId());
            }
        }
        return result;
    }

    /** Role dùng để tính quyền: role trong token, thêm TEAM_LEADER khi đang là tổ trưởng (AUTH-23). */
    public static Set<CareNestRole> permissionRoles(Set<CareNestRole> loginRoles, boolean activeTeamLeader) {
        if (!activeTeamLeader) {
            return loginRoles;
        }
        EnumSet<CareNestRole> roles = EnumSet.of(CareNestRole.TEAM_LEADER);
        roles.addAll(loginRoles);
        return Collections.unmodifiableSet(roles);
    }

    private static boolean academicDutyRevoked(Collection<UserPermissionOverride> overrides) {
        return overrides.stream()
                .anyMatch(o -> Permission.EDU_ACADEMIC_DUTY.name().equals(o.getPermissionCode())
                        && o.getEffect() == PermissionEffect.REVOKE);
    }

    public static boolean isConfigurableForAny(Permission permission, Set<CareNestRole> roles) {
        return roles.stream().anyMatch(permission::isConfigurableFor);
    }
}
