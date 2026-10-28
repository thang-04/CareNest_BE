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
                        Permission.PERMISSION_MANAGE);
        assertThat(Permission.fixedPermissionsOf(CareNestRole.ADMIN))
                .containsExactlyInAnyOrder(Permission.ACCOUNT_MANAGE, Permission.ACCOUNT_STATUS_MANAGE);
        assertThat(Permission.fixedPermissionsOf(CareNestRole.PARENT)).containsExactly(Permission.CHILD_PROFILE_VIEW);
        assertThat(Permission.fixedPermissionsOf(CareNestRole.TEACHER)).isEmpty();
        assertThat(Permission.configurablePermissionsOf(CareNestRole.PRINCIPAL)).isEmpty();
    }

    @Test
    void unknownCodeIsNotResolved() {
        assertThat(Permission.fromCode("CHILD_PROFILE_VIEW")).contains(Permission.CHILD_PROFILE_VIEW);
        assertThat(Permission.fromCode("child.view")).isEmpty();
        assertThat(Permission.fromCode(null)).isEmpty();
    }
}
