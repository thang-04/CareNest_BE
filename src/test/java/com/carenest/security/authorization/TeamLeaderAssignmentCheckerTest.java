package com.carenest.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamLeaderAssignmentCheckerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final StaffAssignmentService staffAssignmentService = mock(StaffAssignmentService.class);
    private final TeamLeaderAssignmentChecker checker =
            new TeamLeaderAssignmentChecker(staffAssignmentService, FIXED_CLOCK);

    @Test
    @DisplayName("AUTH-23: GV có phân công tổ trưởng còn hiệu lực là tổ trưởng; hết hạn thì không")
    void teacherWithActiveAssignmentIsTeamLeader() {
        given(staffAssignmentService.hasActiveTeamLeaderAssignment(5L, TODAY)).willReturn(true);
        given(staffAssignmentService.hasActiveTeamLeaderAssignment(6L, TODAY)).willReturn(false);

        assertThat(checker.isActiveTeamLeader(5L, Set.of(CareNestRole.TEACHER))).isTrue();
        assertThat(checker.isActiveTeamLeader(6L, Set.of(CareNestRole.TEACHER))).isFalse();
    }

    @Test
    @DisplayName("AUTH-23: không có role TEACHER trong token thì không là tổ trưởng")
    void nonTeacherIsNeverTeamLeader() {
        assertThat(checker.isActiveTeamLeader(5L, Set.of(CareNestRole.VICE_PRINCIPAL)))
                .isFalse();
        verifyNoInteractions(staffAssignmentService);
    }
}
