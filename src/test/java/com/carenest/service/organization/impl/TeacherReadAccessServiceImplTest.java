package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeacherReadAccessServiceImplTest {
    private final StaffAssignmentService assignments = mock(StaffAssignmentService.class);
    private final TeacherReadAccessServiceImpl service = new TeacherReadAccessServiceImpl(assignments);

    private LocalDate day(int d) {
        return LocalDate.of(2026, 10, d);
    }

    private StaffAssignmentResponse assignment(long cls, int from, Integer to) {
        return new StaffAssignmentResponse(
                1L, 1L, StaffRole.TEACHER, 1L, cls, day(from), to == null ? null : day(to), false);
    }

    @Test
    void mergesAdjacentAssignmentsButRejectsGapsAndOutsideDates() {
        when(assignments.findAssignments(1L))
                .thenReturn(List.of(
                        assignment(10, 2, 6),
                        assignment(10, 7, 10),
                        assignment(10, 12, null),
                        assignment(11, 1, null)));
        assertThat(service.covers(1L, 10L, day(2), day(10))).isTrue();
        assertThat(service.covers(1L, 10L, day(2), day(12))).isFalse();
        assertThat(service.covers(1L, 10L, day(1), day(2))).isFalse();
        assertThat(service.covers(1L, 10L, day(12), day(30))).isTrue();
        assertThat(service.covers(1L, 12L, day(2), day(2))).isFalse();
    }

    @Test
    void latestDayIgnoresCancelledAssignmentsAndClampsToRequestedYear() {
        when(assignments.findAssignments(1L)).thenReturn(List.of(assignment(10, 10, 9), assignment(10, 12, 15)));
        assertThat(service.latestDay(1L, 10L, day(1), day(11))).isNull();
        assertThat(service.latestDay(1L, 10L, day(1), day(14))).isEqualTo(day(14));
        assertThat(service.latestDay(1L, 10L, day(16), day(30))).isNull();
    }
}
