package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.entity.account.RolePermission;
import com.carenest.entity.account.UserAccount;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.repository.account.RolePermissionRepository;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.repository.account.UserPermissionOverrideRepository;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CampusPermissionRecipientsTest {
    @Test
    void recipientsFollowSchoolPinnedDefaultRevokedAndExpiredScope() {
        var accounts = mock(UserAccountRepository.class);
        var matrix = mock(RolePermissionRepository.class);
        var overrides = mock(UserPermissionOverrideRepository.class);
        var assignments = mock(StaffAssignmentService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-27T03:00:00Z"), ZoneOffset.UTC);
        LocalDate today = LocalDate.now(clock);
        var campusPolicy = new CampusAccessPolicy(assignments, clock);
        var policy = new StaffAccessPolicy(
                assignments,
                mock(EnrollmentService.class),
                mock(ClassroomService.class),
                mock(GuardianChildLinkService.class),
                clock,
                campusPolicy);
        var recipients = new CampusPermissionRecipients(accounts, matrix, overrides, assignments, policy, clock);
        List<UserAccount> users = new ArrayList<>();
        for (long id = 1; id <= 6; id++) {
            var account =
                    UserAccount.create("test-" + id, "test-" + id, null, "Test " + id, CareNestRole.VICE_PRINCIPAL);
            ReflectionTestUtils.setField(account, "id", id);
            users.add(account);
            given(assignments.findActiveCampusIds(id, StaffRole.VICE_PRINCIPAL, today))
                    .willReturn(id == 6 ? Set.of() : Set.of(10L));
            given(assignments.hasActiveCampusAssignment(id, StaffRole.VICE_PRINCIPAL, 10L, today))
                    .willReturn(id != 6);
        }
        given(accounts.findAllByPrimaryRoleAndStatusOrderByFullNameAsc(CareNestRole.VICE_PRINCIPAL, UserStatus.ACTIVE))
                .willReturn(users);
        given(assignments.findPrincipalIds(today)).willReturn(Set.of(9L));
        for (Permission permission : List.of(Permission.CHILD_PROFILE_EDIT, Permission.PARENT_ACCOUNT_MANAGE)) {
            given(matrix.findByRole(CareNestRole.VICE_PRINCIPAL))
                    .willReturn(List.of(RolePermission.create(CareNestRole.VICE_PRINCIPAL, permission.name(), 9L)));
            given(overrides.findByUserId(1L))
                    .willReturn(List.of(UserPermissionOverride.create(
                            1L, permission.name(), PermissionEffect.GRANT, PermissionScope.SCHOOL, null, "Test", 9L)));
            given(overrides.findByUserId(2L))
                    .willReturn(List.of(UserPermissionOverride.create(
                            2L, permission.name(), PermissionEffect.GRANT, PermissionScope.CAMPUS, 20L, "Test", 9L)));
            given(overrides.findByUserId(3L))
                    .willReturn(List.of(UserPermissionOverride.create(
                            3L, permission.name(), PermissionEffect.GRANT, PermissionScope.CAMPUS, 10L, "Test", 9L)));
            given(overrides.findByUserId(5L))
                    .willReturn(List.of(
                            UserPermissionOverride.create(5L, permission.name(), PermissionEffect.REVOKE, "Test", 9L)));
            given(overrides.findByUserId(6L))
                    .willReturn(List.of(UserPermissionOverride.create(
                            6L, permission.name(), PermissionEffect.GRANT, PermissionScope.SCHOOL, null, "Test", 9L)));
            assertThat(recipients.approverIds(permission, 20L)).containsExactlyInAnyOrder(9L, 1L, 2L);
            assertThat(recipients.approverIds(permission, 10L)).containsExactlyInAnyOrder(9L, 1L, 3L, 4L);
        }
    }
}
