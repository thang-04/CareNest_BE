package com.carenest.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.entity.account.RolePermission;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import com.carenest.utils.enums.PermissionScope;
import java.util.List;
import java.util.Map;
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
                .containsExactlyInAnyOrder(Permission.CHILD_PROFILE_VIEW, Permission.ASSESSMENT_VIEW);
        assertThat(resolve(Set.of(CareNestRole.TEACHER), List.of(), List.of()))
                .containsExactly(Permission.ASSESSMENT_VIEW);
    }

    @Test
    @DisplayName("AUTH-10: GRANT cộng thêm, REVOKE thu hồi phần ma trận")
    void grantAddsAndRevokeRemovesConfiguredPermission() {
        assertThat(resolve(Set.of(CareNestRole.TEACHER), List.of(), List.of(override(PermissionEffect.GRANT))))
                .containsExactlyInAnyOrder(Permission.CHILD_PROFILE_VIEW, Permission.ASSESSMENT_VIEW);
        assertThat(resolve(
                        Set.of(CareNestRole.TEACHER),
                        List.of(TEACHER_CHILD_VIEW),
                        List.of(override(PermissionEffect.REVOKE))))
                .containsExactly(Permission.ASSESSMENT_VIEW);
    }

    @Test
    @DisplayName("AUTH-20: GV/tổ trưởng luôn xem đánh giá, kể cả khi ma trận trống (Cần chốt 10/11/2026)")
    void teacherAlwaysViewsAssessment() {
        assertThat(resolve(Set.of(CareNestRole.TEAM_LEADER), List.of(), List.of()))
                .containsExactly(Permission.ASSESSMENT_VIEW);
        // PHT mặc định "Phụ trách chuyên môn" (Tiền 24/11/2026), ngoài ra không có gì khi ma trận trống
        assertThat(resolve(Set.of(CareNestRole.VICE_PRINCIPAL), List.of(), List.of()))
                .containsExactly(Permission.EDU_ACADEMIC_DUTY);
    }

    @Test
    @DisplayName("AUTH-13: REVOKE không trừ phần cố định của role (giáo viên kiêm phụ huynh)")
    void revokeDoesNotRemoveFixedPermission() {
        assertThat(resolve(
                        Set.of(CareNestRole.TEACHER, CareNestRole.PARENT),
                        List.of(TEACHER_CHILD_VIEW),
                        List.of(override(PermissionEffect.REVOKE))))
                .containsExactlyInAnyOrder(Permission.CHILD_PROFILE_VIEW, Permission.ASSESSMENT_VIEW);
    }

    @Test
    @DisplayName("AUTH-10: HT cấp thêm CHILD_PROFILE_EDIT cho GV ⇒ có trong quyền hiệu lực; thu hồi ⇒ mất")
    void teacherGrantOfChildProfileEditIsEffective() {
        UserPermissionOverride grant =
                UserPermissionOverride.create(5L, "CHILD_PROFILE_EDIT", PermissionEffect.GRANT, "hỗ trợ PHT", 1L);

        assertThat(resolve(Set.of(CareNestRole.TEACHER), List.of(), List.of(grant)))
                .contains(Permission.CHILD_PROFILE_EDIT);
        assertThat(resolve(Set.of(CareNestRole.TEACHER), List.of(), List.of()))
                .doesNotContain(Permission.CHILD_PROFILE_EDIT);
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
                .containsExactly(Permission.ASSESSMENT_VIEW);
    }

    @Test
    @DisplayName("AUTH-11: role cố định lấy quyền từ code, không từ ma trận")
    void fixedRolesIgnoreMatrix() {
        assertThat(resolve(Set.of(CareNestRole.ADMIN), List.of(TEACHER_CHILD_VIEW), List.of()))
                .containsExactlyInAnyOrder(Permission.ACCOUNT_MANAGE, Permission.ACCOUNT_STATUS_MANAGE);
    }

    @Test
    @DisplayName("EDU-110: HT bỏ tick mã soạn/duyệt thì mất mã, mã cố định khác vẫn còn")
    void principalEduCodesComeFromMatrix() {
        RolePermission htEdit = RolePermission.create(CareNestRole.PRINCIPAL, "EDU_THEME_PLAN_EDIT", null);
        Set<Permission> ticked = resolve(Set.of(CareNestRole.PRINCIPAL), List.of(htEdit), List.of());
        assertThat(ticked).contains(Permission.EDU_THEME_PLAN_EDIT, Permission.PERMISSION_MANAGE);
        assertThat(ticked).doesNotContain(Permission.EDU_THEME_PLAN_APPROVE);
        Set<Permission> none = resolve(Set.of(CareNestRole.PRINCIPAL), List.of(), List.of());
        assertThat(none).doesNotContainAnyElementsOf(Permission.EDU_WORKFLOW);
        assertThat(none).contains(Permission.EDU_PLAN_CONFIG, Permission.CHILD_ENROLL);
    }

    @Test
    @DisplayName("EDU-110 BR5.1: ngoại lệ cũ của 8 mã soạn/duyệt bị bỏ qua")
    void eduWorkflowOverridesAreIgnored() {
        RolePermission vpApprove = RolePermission.create(CareNestRole.VICE_PRINCIPAL, "EDU_THEME_PLAN_APPROVE", null);
        UserPermissionOverride grant =
                UserPermissionOverride.create(5L, "EDU_TEAM_PLAN_APPROVE", PermissionEffect.GRANT, "cũ", 1L);
        UserPermissionOverride revoke =
                UserPermissionOverride.create(5L, "EDU_THEME_PLAN_APPROVE", PermissionEffect.REVOKE, "cũ", 1L);
        assertThat(resolve(Set.of(CareNestRole.VICE_PRINCIPAL), List.of(vpApprove), List.of(grant, revoke)))
                .contains(Permission.EDU_THEME_PLAN_APPROVE)
                .doesNotContain(Permission.EDU_TEAM_PLAN_APPROVE);
    }

    @Test
    @DisplayName(
            "Tiền 24/11/2026: PHT mặc định chuyên môn nhận cột PHT; REVOKE EDU_ACADEMIC_DUTY ⇒ mất mã soạn/duyệt, cấu hình")
    void nutritionViceLosesEducationPlanCodes() {
        List<RolePermission> vpColumn = List.of(
                RolePermission.create(CareNestRole.VICE_PRINCIPAL, "EDU_SCHOOL_YEAR_PLAN_EDIT", null),
                RolePermission.create(CareNestRole.VICE_PRINCIPAL, "EDU_TEAM_PLAN_APPROVE", null),
                RolePermission.create(CareNestRole.VICE_PRINCIPAL, "CHILD_PROFILE_VIEW", null),
                // Mã chỉ là ngoại lệ theo người: dòng ma trận (nếu có) bị bỏ qua
                RolePermission.create(CareNestRole.VICE_PRINCIPAL, "EDU_ACADEMIC_DUTY", null));
        UserPermissionOverride config =
                UserPermissionOverride.create(5L, "EDU_PLAN_CONFIG", PermissionEffect.GRANT, "chuyên môn", 1L);
        assertThat(resolve(Set.of(CareNestRole.VICE_PRINCIPAL), vpColumn, List.of(config)))
                .contains(
                        Permission.EDU_SCHOOL_YEAR_PLAN_EDIT,
                        Permission.EDU_TEAM_PLAN_APPROVE,
                        Permission.EDU_PLAN_CONFIG,
                        Permission.EDU_ACADEMIC_DUTY);

        UserPermissionOverride nutrition =
                UserPermissionOverride.create(5L, "EDU_ACADEMIC_DUTY", PermissionEffect.REVOKE, "dinh dưỡng", 1L);
        assertThat(resolve(Set.of(CareNestRole.VICE_PRINCIPAL), vpColumn, List.of(config, nutrition)))
                .contains(Permission.CHILD_PROFILE_VIEW)
                .doesNotContain(Permission.EDU_ACADEMIC_DUTY)
                .doesNotContainAnyElementsOf(Permission.EDU_ACADEMIC_ONLY);
        // REVOKE chỉ có tác dụng với PHT; GV bị dòng lệch không mất mã của mình
        RolePermission teacherEdit = RolePermission.create(CareNestRole.TEACHER, "EDU_THEME_PLAN_EDIT", null);
        assertThat(resolve(Set.of(CareNestRole.TEACHER), List.of(teacherEdit), List.of(nutrition)))
                .contains(Permission.EDU_THEME_PLAN_EDIT);
        assertThat(Permission.roleMatrixPermissionsOf(CareNestRole.VICE_PRINCIPAL))
                .doesNotContain(Permission.EDU_ACADEMIC_DUTY);
        assertThat(Permission.configurablePermissionsOf(CareNestRole.VICE_PRINCIPAL))
                .contains(Permission.EDU_ACADEMIC_DUTY);
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

    @Test
    @DisplayName("AUTH-20: chỉ GRANT phạm vi điểm trường có ID điểm trường của mã sửa theo mảng mới là giao cụ thể")
    void pinnedCampusOnlyFromCampusGrantWithCampusId() {
        UserPermissionOverride pinned = UserPermissionOverride.create(
                5L, "FOOD_SAFETY_APPROVE", PermissionEffect.GRANT, PermissionScope.CAMPUS, 3L, "cơ sở 3", 1L);
        UserPermissionOverride assigned = UserPermissionOverride.create(
                5L, "MEAL_COUNT_CONFIRM", PermissionEffect.GRANT, PermissionScope.CAMPUS, "cơ sở phụ trách", 1L);

        assertThat(EffectivePermissionResolver.pinnedCampuses(
                        Set.of(CareNestRole.VICE_PRINCIPAL), List.of(pinned, assigned)))
                .containsExactly(Map.entry(Permission.FOOD_SAFETY_APPROVE, 3L));
        assertThat(EffectivePermissionResolver.pinnedCampuses(Set.of(CareNestRole.TEACHER), List.of(pinned)))
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
