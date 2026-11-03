package com.carenest.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.entity.account.RolePermission;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import com.carenest.utils.enums.PermissionScope;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EffectivePermissionResolverTest {

    private static final RolePermission TEACHER_CHILD_VIEW =
            RolePermission.create(CareNestRole.TEACHER, "CHILD_PROFILE_VIEW", null);

    @Test
    @DisplayName("AUTH-10: role cấu hình được lấy quyền từ ma trận")
    void configurableRoleTakesMatrixRows() {
        assertThat(resolve(Set.of(CareNestRole.TEACHER), List.of(TEACHER_CHILD_VIEW), List.of()))
                .containsExactly(Permission.CHILD_PROFILE_VIEW);
        assertThat(resolve(Set.of(CareNestRole.TEACHER), List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("AUTH-10: GRANT cộng thêm, REVOKE thu hồi phần ma trận")
    void grantAddsAndRevokeRemovesConfiguredPermission() {
        assertThat(resolve(Set.of(CareNestRole.TEACHER), List.of(), List.of(override(PermissionEffect.GRANT))))
                .containsExactly(Permission.CHILD_PROFILE_VIEW);
        assertThat(resolve(
                        Set.of(CareNestRole.TEACHER),
                        List.of(TEACHER_CHILD_VIEW),
                        List.of(override(PermissionEffect.REVOKE))))
                .isEmpty();
    }

    @Test
    @DisplayName("AUTH-13: REVOKE không trừ phần cố định của role (giáo viên kiêm phụ huynh)")
    void revokeDoesNotRemoveFixedPermission() {
        assertThat(resolve(
                        Set.of(CareNestRole.TEACHER, CareNestRole.PARENT),
                        List.of(TEACHER_CHILD_VIEW),
                        List.of(override(PermissionEffect.REVOKE))))
                .containsExactly(Permission.CHILD_PROFILE_VIEW);
    }

    @Test
    @DisplayName("AUTH-13: GRANT ngoài configurableRoles và dòng ma trận lệch bị bỏ qua")
    void grantOutsideConfigurableRolesAndStaleRowsAreIgnored() {
        UserPermissionOverride grantPermissionManage =
                UserPermissionOverride.create(5L, "PERMISSION_MANAGE", PermissionEffect.GRANT, "leo quyền", 1L);
        RolePermission kitchenChildView = RolePermission.create(CareNestRole.KITCHEN_STAFF, "CHILD_PROFILE_VIEW", null);
        RolePermission unknownCode = RolePermission.create(CareNestRole.TEACHER, "REMOVED_CODE", null);

        assertThat(resolve(
                        Set.of(CareNestRole.KITCHEN_STAFF),
                        List.of(kitchenChildView, unknownCode),
                        List.of(grantPermissionManage, override(PermissionEffect.GRANT))))
                .isEmpty();
    }

    @Test
    @DisplayName("AUTH-23: chỉ thêm TEAM_LEADER vào role tính quyền khi đang là tổ trưởng")
    void teamLeaderColumnAddedOnlyForActiveTeamLeader() {
        assertThat(EffectivePermissionResolver.permissionRoles(Set.of(CareNestRole.TEACHER), false))
                .containsExactly(CareNestRole.TEACHER);
        assertThat(EffectivePermissionResolver.permissionRoles(Set.of(CareNestRole.TEACHER), true))
                .containsExactlyInAnyOrder(CareNestRole.TEACHER, CareNestRole.TEAM_LEADER);
    }

    @Test
    @DisplayName("AUTH-23: ô ma trận cột TEAM_LEADER chỉ áp cho người đang là tổ trưởng")
    void teamLeaderMatrixRowNeedsTeamLeaderRole() {
        RolePermission teamLeaderRow = RolePermission.create(CareNestRole.TEAM_LEADER, "CHILD_PROFILE_VIEW", null);

        assertThat(resolve(Set.of(CareNestRole.TEACHER), List.of(teamLeaderRow), List.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("AUTH-11: role cố định lấy quyền từ code, không từ ma trận")
    void fixedRolesIgnoreMatrix() {
        assertThat(resolve(Set.of(CareNestRole.ADMIN), List.of(TEACHER_CHILD_VIEW), List.of()))
                .containsExactlyInAnyOrder(Permission.ACCOUNT_MANAGE, Permission.ACCOUNT_STATUS_MANAGE);
    }

    @Test
    @DisplayName("AUTH-20: chỉ GRANT phạm vi cả trường của mã sửa theo mảng mới là quyền cả trường")
    void schoolWideOnlyFromSchoolGrantOfCampusScopedPermission() {
        UserPermissionOverride schoolGrant = UserPermissionOverride.create(
                5L, "MEAL_COUNT_CONFIRM", PermissionEffect.GRANT, PermissionScope.SCHOOL, "dinh dưỡng", 1L);
        UserPermissionOverride notScoped = UserPermissionOverride.create(
                5L, "CHILD_PROFILE_VIEW", PermissionEffect.GRANT, PermissionScope.SCHOOL, "lệch", 1L);

        assertThat(EffectivePermissionResolver.schoolWide(
                        Set.of(CareNestRole.VICE_PRINCIPAL), List.of(schoolGrant, notScoped)))
                .containsExactly(Permission.MEAL_COUNT_CONFIRM);
        assertThat(EffectivePermissionResolver.schoolWide(Set.of(CareNestRole.TEACHER), List.of(schoolGrant)))
                .isEmpty();
        assertThat(EffectivePermissionResolver.schoolWide(
                        Set.of(CareNestRole.VICE_PRINCIPAL),
                        List.of(UserPermissionOverride.create(
                                5L, "MEAL_COUNT_CONFIRM", PermissionEffect.GRANT, "điểm trường", 1L))))
                .isEmpty();
    }

    private static Set<Permission> resolve(
            Set<CareNestRole> roles, List<RolePermission> rows, List<UserPermissionOverride> overrides) {
        return EffectivePermissionResolver.resolve(roles, rows, overrides);
    }

    private static UserPermissionOverride override(PermissionEffect effect) {
        return UserPermissionOverride.create(5L, "CHILD_PROFILE_VIEW", effect, "lý do", 1L);
    }
}
