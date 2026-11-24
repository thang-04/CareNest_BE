package com.carenest.utils.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionTest {

    @Test
    @DisplayName("AUTH-11: PERMISSION_MANAGE chỉ PRINCIPAL có và không giao được cho ai")
    void permissionManageIsPrincipalOnlyAndNotDelegable() {
        assertThat(Permission.PERMISSION_MANAGE.defaultRoles()).containsExactly(CareNestRole.PRINCIPAL);
        assertThat(Permission.PERMISSION_MANAGE.configurableRoles()).isEmpty();
    }

    @Test
    @DisplayName("AUTH-11: chỉ VICE_PRINCIPAL/TEACHER/KITCHEN_STAFF có ô cấu hình")
    void onlyConfigurableRolesAppearInConfigurableRoles() {
        Arrays.stream(Permission.values())
                .forEach(permission -> assertThat(Permission.CONFIGURABLE_ROLES)
                        .as(permission.name())
                        .containsAll(permission.configurableRoles()));
    }

    @Test
    @DisplayName("AUTH-05: bếp không bao giờ được cấu hình quyền xem hồ sơ trẻ")
    void kitchenStaffCannotBeGrantedChildData() {
        assertThat(Permission.CHILD_PROFILE_VIEW.isConfigurableFor(CareNestRole.KITCHEN_STAFF))
                .isFalse();
        assertThat(Permission.CHILD_PROFILE_VIEW.defaultRoles()).doesNotContain(CareNestRole.KITCHEN_STAFF);
    }

    @Test
    @DisplayName("CHD-01: tiếp nhận, sửa hồ sơ, xếp lớp — HT cố định, PHT mặc định bật và HT tắt được, GV/bếp không có")
    void childEnrollmentPermissionsArePrincipalFixedAndVicePrincipalConfigurable() {
        for (Permission permission :
                EnumSet.of(Permission.CHILD_ENROLL, Permission.CHILD_PROFILE_EDIT, Permission.CHILD_PLACEMENT)) {
            assertThat(permission.defaultRoles())
                    .as(permission.name())
                    .containsExactlyInAnyOrder(CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL);
        }
        assertThat(Permission.CHILD_ENROLL.configurableRoles()).containsExactly(CareNestRole.VICE_PRINCIPAL);
        assertThat(Permission.CHILD_PLACEMENT.configurableRoles()).containsExactly(CareNestRole.VICE_PRINCIPAL);
        // AUTH-10 (2026-11-15): HT cấp thêm sửa hồ sơ cho GV được, GV mặc định không có
        assertThat(Permission.CHILD_PROFILE_EDIT.configurableRoles())
                .containsExactlyInAnyOrder(CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER);
        assertThat(Permission.CHILD_PROFILE_EDIT.defaultRoles()).doesNotContain(CareNestRole.TEACHER);
    }

    @Test
    void fixedRolesTakePermissionsFromCodeAndConfigurableRolesFromMatrix() {
        assertThat(Permission.fixedPermissionsOf(CareNestRole.PRINCIPAL))
                .containsExactlyInAnyOrder(
                        Permission.CHILD_PROFILE_VIEW,
                        Permission.CHILD_ENROLL,
                        Permission.CHILD_PROFILE_EDIT,
                        Permission.CHILD_PLACEMENT,
                        Permission.ACCOUNT_MANAGE,
                        Permission.ACCOUNT_STATUS_MANAGE,
                        Permission.STAFF_ASSIGNMENT_MANAGE,
                        Permission.PARENT_ACCOUNT_MANAGE,
                        Permission.YEAR_PLACEMENT_MANAGE,
                        Permission.ROOM_MANAGE,
                        Permission.ATTENDANCE_VIEW,
                        Permission.ATTENDANCE_UNLOCK,
                        Permission.MEAL_COUNT_CONFIRM,
                        Permission.ASSESSMENT_VIEW,
                        Permission.ASSESSMENT_UNLOCK,
                        Permission.ASSESSMENT_CLASS_REVIEW,
                        Permission.NUTRITION_MANAGE,
                        Permission.MEAL_CONFIG_MANAGE,
                        Permission.MENU_MANAGE,
                        Permission.STOCK_MANAGE,
                        Permission.FOOD_SAFETY_APPROVE,
                        Permission.EDU_PLAN_CONFIG,
                        Permission.PERMISSION_MANAGE);
        assertThat(Permission.fixedPermissionsOf(CareNestRole.ADMIN))
                .containsExactlyInAnyOrder(Permission.ACCOUNT_MANAGE, Permission.ACCOUNT_STATUS_MANAGE);
        assertThat(Permission.fixedPermissionsOf(CareNestRole.PARENT)).containsExactly(Permission.CHILD_PROFILE_VIEW);
        // AUTH-20 (Cần chốt 10/11/2026): GV/tổ trưởng luôn xem đánh giá, không nằm trong ma trận
        assertThat(Permission.fixedPermissionsOf(CareNestRole.TEACHER)).containsExactly(Permission.ASSESSMENT_VIEW);
        assertThat(Permission.fixedPermissionsOf(CareNestRole.TEAM_LEADER)).containsExactly(Permission.ASSESSMENT_VIEW);
        assertThat(Permission.fixedPermissionsOf(CareNestRole.VICE_PRINCIPAL)).isEmpty();
        // EDU-110: HT chỉ có ô cho đúng 8 mã soạn/duyệt
        assertThat(Permission.configurablePermissionsOf(CareNestRole.PRINCIPAL))
                .containsExactlyInAnyOrderElementsOf(Permission.EDU_WORKFLOW);
    }

    @Test
    @DisplayName("EDU-110 BR5.1: 8 mã soạn/duyệt không ghi đè theo người, mã khác vẫn được")
    void eduWorkflowCodesAreNotOverridable() {
        assertThat(Permission.EDU_WORKFLOW).hasSize(8).noneMatch(Permission::overridable);
        assertThat(Permission.EDU_PLAN_CONFIG.overridable()).isTrue();
        assertThat(Permission.CHILD_PROFILE_VIEW.overridable()).isTrue();
        assertThat(Permission.CONFIGURABLE_ROLES).contains(CareNestRole.PRINCIPAL);
        assertThat(Permission.CHILD_ENROLL.isConfigurableFor(CareNestRole.PRINCIPAL))
                .isFalse();
    }

    @Test
    @DisplayName("Ba phân hệ nghiệp vụ chỉ giao quyền cho đúng vai trò, HT giữ quyền cố định")
    void schoolAttendanceAndAssessmentPermissionsHaveSafeDelegationTargets() {
        assertThat(Permission.ROOM_MANAGE.configurableRoles()).containsExactly(CareNestRole.VICE_PRINCIPAL);
        assertThat(Permission.ATTENDANCE_RECORD.configurableRoles())
                .containsExactlyInAnyOrder(CareNestRole.TEACHER, CareNestRole.TEAM_LEADER);
        assertThat(Permission.ASSESSMENT_RECORD.configurableRoles())
                .containsExactlyInAnyOrder(CareNestRole.TEACHER, CareNestRole.TEAM_LEADER);
        assertThat(Permission.ASSESSMENT_VIEW.configurableRoles()).containsExactly(CareNestRole.VICE_PRINCIPAL);
        assertThat(Permission.ASSESSMENT_UNLOCK.configurableRoles()).containsExactly(CareNestRole.VICE_PRINCIPAL);
        assertThat(Permission.REWARD_REVIEW.configurableRoles()).containsExactly(CareNestRole.VICE_PRINCIPAL);
    }

    @Test
    @DisplayName("Quyền quản lý theo điểm trường cho phép HT cấp phạm vi toàn trường")
    void campusManagedPermissionsSupportSchoolWideOverrides() {
        assertThat(EnumSet.of(
                        Permission.STAFF_ASSIGNMENT_MANAGE,
                        Permission.YEAR_PLACEMENT_MANAGE,
                        Permission.ROOM_MANAGE,
                        Permission.ATTENDANCE_VIEW,
                        Permission.ATTENDANCE_UNLOCK,
                        Permission.ASSESSMENT_VIEW,
                        Permission.ASSESSMENT_UNLOCK,
                        Permission.ASSESSMENT_CLASS_REVIEW,
                        Permission.REWARD_REVIEW))
                .allMatch(Permission::campusScoped);

        assertThat(EnumSet.of(
                        Permission.ATTENDANCE_RECORD,
                        Permission.DAILY_HEALTH_RECORD,
                        Permission.PICKUP_RECORD,
                        Permission.ASSESSMENT_RECORD,
                        Permission.ASSESSMENT_CONFIRM))
                .noneMatch(Permission::campusScoped);
    }

    @Test
    @DisplayName("EDU-108 FR1: 9 mã kế hoạch giáo dục, mặc định giữ hành vi, không theo điểm trường")
    void educationPlanPermissions() {
        var vp = CareNestRole.VICE_PRINCIPAL;
        var gv = CareNestRole.TEACHER;
        var tt = CareNestRole.TEAM_LEADER;
        var ht = CareNestRole.PRINCIPAL;
        // EDU_ACADEMIC_DUTY (Tiền 24/11/2026) chỉ là ngoại lệ theo PHT, không phải quyền mặc định của vai trò nào
        assertThat(Permission.EDU_ACADEMIC_DUTY.defaultRoles()).isEmpty();
        assertThat(Permission.EDU_ACADEMIC_DUTY.configurableRoles()).containsExactly(vp);
        assertThat(Permission.EDU_ACADEMIC_DUTY.inRoleMatrix()).isFalse();
        assertThat(Arrays.stream(Permission.values())
                        .filter(p -> p.module().equals("education-plan") && p != Permission.EDU_ACADEMIC_DUTY))
                .hasSize(9)
                .noneMatch(Permission::campusScoped)
                .allMatch(p -> p.defaultRoles().contains(ht));
        assertThat(Permission.EDU_SCHOOL_YEAR_PLAN_EDIT.defaultRoles()).containsExactlyInAnyOrder(ht, vp);
        assertThat(Permission.EDU_SCHOOL_YEAR_PLAN_EDIT.configurableRoles()).containsExactlyInAnyOrder(ht, vp, gv, tt);
        assertThat(Permission.EDU_SCHOOL_YEAR_PLAN_APPROVE.configurableRoles()).containsExactlyInAnyOrder(ht, vp, tt);
        assertThat(Permission.EDU_TEAM_PLAN_EDIT.defaultRoles()).containsExactlyInAnyOrder(ht, vp, tt);
        assertThat(Permission.EDU_TEAM_PLAN_APPROVE.defaultRoles()).containsExactlyInAnyOrder(ht, vp);
        assertThat(Permission.EDU_SEMESTER_PACKAGE_EDIT.defaultRoles()).containsExactlyInAnyOrder(ht, vp, gv, tt);
        assertThat(Permission.EDU_SEMESTER_PACKAGE_APPROVE.defaultRoles()).containsExactlyInAnyOrder(ht, vp, tt);
        assertThat(Permission.EDU_THEME_PLAN_EDIT.defaultRoles()).containsExactlyInAnyOrder(ht, vp, gv, tt);
        assertThat(Permission.EDU_THEME_PLAN_APPROVE.defaultRoles()).containsExactlyInAnyOrder(ht, vp, tt);
        assertThat(Permission.EDU_THEME_PLAN_APPROVE.configurableRoles()).doesNotContain(gv);
        // Quyết định 11/11: cấu hình chỉ HT, PHT mặc định tắt
        assertThat(Permission.EDU_PLAN_CONFIG.defaultRoles()).containsExactly(ht);
        assertThat(Permission.EDU_PLAN_CONFIG.configurableRoles()).containsExactly(vp);
    }

    @Test
    void unknownCodeIsNotResolved() {
        assertThat(Permission.fromCode("CHILD_PROFILE_VIEW")).contains(Permission.CHILD_PROFILE_VIEW);
        assertThat(Permission.fromCode("child.view")).isEmpty();
        assertThat(Permission.fromCode(null)).isEmpty();
    }
}
