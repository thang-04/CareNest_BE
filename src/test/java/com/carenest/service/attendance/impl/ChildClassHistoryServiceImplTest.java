package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.carenest.service.child.EnrollmentService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChildClassHistoryServiceImplTest {
    @Test
    void labelsUseActualMembershipAndDoNotGuessMissingRecords() {
        var enrollment = mock(EnrollmentService.class);
        var date = LocalDate.of(2026, 11, 23);
        var ids = List.of(1L, 2L, 3L, 4L);
        when(enrollment.findActiveClassroomIdByChildIds(ids, date)).thenReturn(Map.of(1L, 10L, 2L, 11L));
        when(enrollment.findChildIdsLeftSchool(ids, date)).thenReturn(Set.of(3L));
        var result = new ChildClassHistoryServiceImpl(enrollment).statuses(10L, ids, date);
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(2L, "TRANSFERRED", 3L, "LEFT_SCHOOL"));
    }
}
