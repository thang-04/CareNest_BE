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
    @DisplayName("AUTH-20: PHT không có quyền xem liên cơ sở thì không xem được campus khác")
    void vicePrincipalWithoutAllCampusViewCannotViewOtherCampus() {
        CurrentUser vicePrincipal = user(CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.findActiveCampusIds(vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of(CAMPUS_A));

        assertThat(policy.canViewCampus(vicePrincipal, CAMPUS_B)).isFalse();
    }

    @Test
    @DisplayName("AUTH-20: PHT được cấp xem liên cơ sở thì xem được campus khác")
    void vicePrincipalWithAllCampusViewCanViewOtherCampus() {
        CurrentUser vicePrincipal = userWith(Set.of(Permission.ALL_CAMPUS_VIEW), CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.findActiveCampusIds(vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of(CAMPUS_A));

        assertThat(policy.canViewCampus(vicePrincipal, CAMPUS_B)).isTrue();
    }

    @Test
    @DisplayName("AUTH-20: hết phân công PHT thì quyền xem liên cơ sở không còn tác dụng")
    void allCampusViewNeedsActiveVicePrincipalAssignment() {
        CurrentUser vicePrincipal = userWith(Set.of(Permission.ALL_CAMPUS_VIEW), CareNestRole.VICE_PRINCIPAL);
        given(staffAssignmentService.findActiveCampusIds(vicePrincipal.userId(), StaffRole.VICE_PRINCIPAL, TODAY))
                .willReturn(Set.of());

        assertThat(policy.canViewCampus(vicePrincipal, CAMPUS_A)).isFalse();
        assertThat(policy.canViewCampus(vicePrincipal, CAMPUS_B)).isFalse();
    }

    @Test
    @DisplayName("AUTH-20: quyền xem liên cơ sở chỉ áp cho PHT")
    void allCampusViewIsIgnoredForOtherRoles() {
        CurrentUser teacher = userWith(Set.of(Permission.ALL_CAMPUS_VIEW), CareNestRole.TEACHER);

        assertThat(policy.canViewCampus(teacher, CAMPUS_B)).isFalse();
        verifyNoInteractions(staffAssignmentService);
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
                .hasMessage("You do not have access to this campus")
                .extracting("apiCode")
                .isEqualTo(ApiCode.FORBIDDEN);
    }

    static CurrentUser user(CareNestRole... roles) {
        return new CurrentUser(7L, "kc-7", "demo.user", "Demo User", null, Set.of(roles));
    }

    static CurrentUser userWith(Set<Permission> permissions, CareNestRole... roles) {
        return new CurrentUser(7L, "kc-7", "demo.user", "Demo User", null, Set.of(roles), permissions);
    }
}
