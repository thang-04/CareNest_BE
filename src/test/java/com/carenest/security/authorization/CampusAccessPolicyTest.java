package com.carenest.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CampusAccessPolicyTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final long CAMPUS_A = 1L;
    private static final long CAMPUS_B = 2L;

    private final StaffAssignmentService staffAssignmentService = mock(StaffAssignmentService.class);
    private final CampusAccessPolicy policy = new CampusAccessPolicy(staffAssignmentService, FIXED_CLOCK);

    @Test
    void principalWithActiveAssignmentCanViewEveryCampus() {
        CurrentUser principal = user(CareNestRole.PRINCIPAL);
        given(staffAssignmentService.hasActivePrincipalAssignment(principal.userId(), TODAY))
                .willReturn(true);

        assertThat(policy.canViewCampus(principal, CAMPUS_A)).isTrue();
        assertThat(policy.canViewCampus(principal, CAMPUS_B)).isTrue();
        assertThat(policy.canReadCampus(principal, CAMPUS_B)).isTrue();
    }

    @Test
    void principalRoleWithoutActiveAssignmentIsDenied() {
        CurrentUser principal = user(CareNestRole.PRINCIPAL);
        given(staffAssignmentService.hasActivePrincipalAssignment(principal.userId(), TODAY))
                .willReturn(false);

        assertThat(policy.canViewCampus(principal, CAMPUS_A)).isFalse();
    }

    @Test
    void vicePrincipalCanViewOnlyAssignedCampus() {
        CurrentUser vicePrincipal = user(CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.hasActiveCampusAssignment(
                        vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, CAMPUS_A, TODAY))
                .willReturn(true);

        assertThat(policy.canViewCampus(vicePrincipal, CAMPUS_A)).isTrue();
        assertThat(policy.canViewCampus(vicePrincipal, CAMPUS_B)).isFalse();
    }

    @Test
    @DisplayName("AUTH-20: PHT còn phân công đọc được mọi campus mà không cần quyền thêm")
    void vicePrincipalWithActiveAssignmentReadsEveryCampus() {
        CurrentUser vicePrincipal = user(CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.findActiveCampusIds(vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of(CAMPUS_A));

        assertThat(policy.canReadAllCampuses(vicePrincipal)).isTrue();
        assertThat(policy.canReadCampus(vicePrincipal, CAMPUS_A)).isTrue();
        assertThat(policy.canReadCampus(vicePrincipal, CAMPUS_B)).isTrue();
    }

    @Test
    @DisplayName("AUTH-20: đọc mọi campus không mở phạm vi thao tác; thao tác vẫn chỉ campus được phân công")
    void readingEveryCampusNeverWidensActionScope() {
        CurrentUser vicePrincipal = user(CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.hasActiveCampusAssignment(
                        vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, CAMPUS_A, TODAY))
                .willReturn(true);
        given(staffAssignmentService.findActiveCampusIds(vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of(CAMPUS_A));

        assertThat(policy.canViewCampus(vicePrincipal, CAMPUS_A)).isTrue();
        assertThat(policy.canViewCampus(vicePrincipal, CAMPUS_B)).isFalse();
        assertThat(policy.canReadCampus(vicePrincipal, CAMPUS_B)).isTrue();
    }

    @Test
    @DisplayName("AUTH-20: hết phân công PHT thì không đọc được campus nào")
    void readingNeedsActiveVicePrincipalAssignment() {
        CurrentUser vicePrincipal = user(CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.findActiveCampusIds(vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of());

        assertThat(policy.canReadAllCampuses(vicePrincipal)).isFalse();
        assertThat(policy.canReadCampus(vicePrincipal, CAMPUS_A)).isFalse();
        assertThat(policy.canReadCampus(vicePrincipal, CAMPUS_B)).isFalse();
    }

    @Test
    @DisplayName("AUTH-20: đọc mọi campus chỉ áp cho HT và PHT")
    void readingEveryCampusIsOnlyForSchoolManagement() {
        CurrentUser teacher = user(CareNestRole.TEACHER);
        CurrentUser kitchen = user(CareNestRole.KITCHEN_STAFF);

        assertThat(policy.canReadCampus(teacher, CAMPUS_B)).isFalse();
        assertThat(policy.canReadCampus(kitchen, CAMPUS_A)).isFalse();
        verifyNoInteractions(staffAssignmentService);
    }

    @Test
    @DisplayName("AUTH-20: mã sửa theo mảng phạm vi điểm trường chỉ sửa được campus đang phân công")
    void campusScopedPermissionEditsOnlyAssignedCampus() {
        CurrentUser vicePrincipal = userWith(Set.of(Permission.MEAL_COUNT_CONFIRM), Set.of());
        given(staffAssignmentService.hasActiveCampusAssignment(
                        vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, CAMPUS_A, TODAY))
                .willReturn(true);

        assertThat(policy.canEditCampus(vicePrincipal, Permission.MEAL_COUNT_CONFIRM, CAMPUS_A))
                .isTrue();
        assertThat(policy.canEditCampus(vicePrincipal, Permission.MEAL_COUNT_CONFIRM, CAMPUS_B))
                .isFalse();
    }

    @Test
    @DisplayName("AUTH-20: GRANT phạm vi cả trường sửa được mọi campus khi còn phân công PHT")
    void schoolWidePermissionEditsEveryCampusWhileAssigned() {
        CurrentUser vicePrincipal =
                userWith(Set.of(Permission.MEAL_COUNT_CONFIRM), Set.of(Permission.MEAL_COUNT_CONFIRM));
        given(staffAssignmentService.findActiveCampusIds(vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of(CAMPUS_A), Set.of());

        assertThat(policy.canEditCampus(vicePrincipal, Permission.MEAL_COUNT_CONFIRM, CAMPUS_B))
                .isTrue();
        assertThat(policy.canEditCampus(vicePrincipal, Permission.MEAL_COUNT_CONFIRM, CAMPUS_B))
                .isFalse();
    }

    @Test
    @DisplayName("AUTH-20: bị thu hồi mã sửa thì không sửa được kể cả campus mình; HT luôn sửa được")
    void editNeedsPermissionCodeExceptForPrincipal() {
        CurrentUser revoked = userWith(Set.of(), Set.of());
        CurrentUser principal = new CurrentUser(
                8L,
                "kc-8",
                "principal",
                "HT",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                Set.of(Permission.MEAL_COUNT_CONFIRM));
        given(staffAssignmentService.hasActivePrincipalAssignment(8L, TODAY)).willReturn(true);

        assertThat(policy.canEditCampus(revoked, Permission.MEAL_COUNT_CONFIRM, CAMPUS_A))
                .isFalse();
        assertThat(policy.canEditCampus(principal, Permission.MEAL_COUNT_CONFIRM, CAMPUS_B))
                .isTrue();
    }

    @Test
    void kitchenStaffCannotViewCampusDataEvenWithCampusAssignment() {
        CurrentUser kitchen = user(CareNestRole.KITCHEN_STAFF);

        assertThat(policy.canViewCampus(kitchen, CAMPUS_A)).isFalse();
        verifyNoInteractions(staffAssignmentService);
    }

    @Test
    void assignmentWithoutMatchingTokenRoleIsIgnored() {
        CurrentUser teacher = user(CareNestRole.TEACHER);
        given(staffAssignmentService.hasActiveCampusAssignment(anyLong(), any(), anyLong(), any()))
                .willReturn(true);

        assertThat(policy.canViewCampus(teacher, CAMPUS_A)).isFalse();
    }

    @Test
    void checkThrowsForbiddenWhenOutOfScope() {
        CurrentUser admin = user(CareNestRole.ADMIN);

        assertThatThrownBy(() -> policy.checkCanViewCampus(admin, CAMPUS_A))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.CAMPUS_ACCESS_DENIED.getDefaultDesc())
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.FORBIDDEN.getHttpStatus());
        assertThatThrownBy(() -> policy.checkCanReadCampus(admin, CAMPUS_A))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.CAMPUS_ACCESS_DENIED.getDefaultDesc())
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.FORBIDDEN.getHttpStatus());
    }

    static CurrentUser user(CareNestRole... roles) {
        return new CurrentUser(7L, "kc-7", "demo.user", "Demo User", null, Set.of(roles));
    }

    private static CurrentUser userWith(Set<Permission> permissions, Set<Permission> schoolWide) {
        return new CurrentUser(
                7L,
                "kc-7",
                "demo.user",
                "Demo User",
                null,
                Set.of(CareNestRole.VICE_PRINCIPAL),
                permissions,
                schoolWide);
    }
}
