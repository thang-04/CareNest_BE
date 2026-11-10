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
            assertThat(permission.configurableRoles())
                    .as(permission.name())
                    .containsExactly(CareNestRole.VICE_PRINCIPAL);
        }
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
                        Permission.MENU_MANAGE,
                        Permission.STOCK_MANAGE,
                        Permission.FOOD_SAFETY_APPROVE,
                        Permission.PERMISSION_MANAGE);
        assertThat(Permission.fixedPermissionsOf(CareNestRole.ADMIN))
                .containsExactlyInAnyOrder(Permission.ACCOUNT_MANAGE, Permission.ACCOUNT_STATUS_MANAGE);
        assertThat(Permission.fixedPermissionsOf(CareNestRole.PARENT)).containsExactly(Permission.CHILD_PROFILE_VIEW);
        assertThat(Permission.fixedPermissionsOf(CareNestRole.TEACHER)).isEmpty();
        assertThat(Permission.configurablePermissionsOf(CareNestRole.PRINCIPAL)).isEmpty();
    }

    @Test
    @DisplayName("Ba phân hệ nghiệp vụ chỉ giao quyền cho đúng vai trò, HT giữ quyền cố định")
    void schoolAttendanceAndAssessmentPermissionsHaveSafeDelegationTargets() {
        assertThat(Permission.ROOM_MANAGE.configurableRoles()).containsExactly(CareNestRole.VICE_PRINCIPAL);
        assertThat(Permission.ATTENDANCE_RECORD.configurableRoles())
                .containsExactlyInAnyOrder(CareNestRole.TEACHER, CareNestRole.TEAM_LEADER);
        assertThat(Permission.ASSESSMENT_RECORD.configurableRoles())
                .containsExactlyInAnyOrder(CareNestRole.TEACHER, CareNestRole.TEAM_LEADER);
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
    void unknownCodeIsNotResolved() {
        assertThat(Permission.fromCode("CHILD_PROFILE_VIEW")).contains(Permission.CHILD_PROFILE_VIEW);
        assertThat(Permission.fromCode("child.view")).isEmpty();
        assertThat(Permission.fromCode(null)).isEmpty();
    }
}
