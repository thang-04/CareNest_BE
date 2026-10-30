package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.account.MyAccountResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final StaffAssignmentService staffAssignmentService = mock(StaffAssignmentService.class);
    private final GuardianChildLinkService guardianChildLinkService = mock(GuardianChildLinkService.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final AccountServiceImpl accountService = new AccountServiceImpl(
            staffAssignmentService, guardianChildLinkService, userAccountRepository, FIXED_CLOCK);

    @Test
    void teamLeaderAssignmentCountsForTeacherToken() {
        CurrentUser teacher = user(CareNestRole.TEACHER);
        given(staffAssignmentService.findActiveAssignments(teacher.userId(), TODAY))
                .willReturn(List.of(new StaffAssignmentSummaryResponse(
                        StaffRole.AGE_GROUP_TEAM_LEADER, null, null, 3L, TODAY.minusMonths(1), null)));

        MyAccountResponse response = accountService.getMyAccount(teacher);

        assertThat(response.assignments())
                .extracting(StaffAssignmentSummaryResponse::ageGroupId)
                .containsExactly(3L);
    }

    @Test
    void scopeSummaryKeepsOnlyAssignmentsMatchingTokenRoles() {
        CurrentUser teacher = user(CareNestRole.TEACHER);
        given(staffAssignmentService.findActiveAssignments(teacher.userId(), TODAY))
                .willReturn(List.of(
                        new StaffAssignmentSummaryResponse(
                                StaffRole.TEACHER, null, 11L, null, TODAY.minusMonths(1), null),
                        // phân công bếp còn trong DB nhưng token không có role KITCHEN_STAFF ⇒ không có hiệu lực
                        new StaffAssignmentSummaryResponse(
                                StaffRole.KITCHEN_STAFF, 1L, null, null, TODAY.minusMonths(1), null)));

        MyAccountResponse response = accountService.getMyAccount(teacher);

        assertThat(response.assignments())
                .extracting(StaffAssignmentSummaryResponse::staffRole)
                .containsExactly(StaffRole.TEACHER);
        assertThat(response.childIds()).isEmpty();
        verifyNoInteractions(guardianChildLinkService);
    }

    @Test
    void parentSeesLinkedChildIds() {
        CurrentUser parent = user(CareNestRole.PARENT);
        given(staffAssignmentService.findActiveAssignments(parent.userId(), TODAY))
                .willReturn(List.of());
        given(guardianChildLinkService.findChildIdsOfGuardian(parent.userId())).willReturn(List.of(101L));

        MyAccountResponse response = accountService.getMyAccount(parent);

        assertThat(response.childIds()).containsExactly(101L);
        assertThat(response.roles()).containsExactly(CareNestRole.PARENT);
    }

    private static CurrentUser user(CareNestRole... roles) {
        return new CurrentUser(7L, "kc-7", "demo.user", "Demo User", null, Set.of(roles));
    }
}
