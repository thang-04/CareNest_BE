package com.carenest.service.observation.impl;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.repository.observation.*;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.observation.YearEndSheetService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.enums.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvaluationLockOrderTest {
    @Test
    void locksClassesByIdRegardlessOfDisplayOrder() {
        var rules = mock(ObservationDayRules.class);
        var classes = mock(ClassroomService.class);
        var evaluations = mock(ChildEvaluationRepository.class);
        var service = new ChildEvaluationServiceImpl(
                rules,
                classes,
                mock(SchoolYearService.class),
                mock(AgeGroupService.class),
                mock(EnrollmentService.class),
                mock(AccountService.class),
                mock(EvaluationSources.class),
                evaluations,
                mock(ChildEvaluationEventRepository.class),
                mock(DailyObservationRepository.class),
                mock(BehaviourTicketRepository.class),
                mock(YearEndSheetService.class));
        var user = new CurrentUser(1L, "demo", "demo", "Demo", null, Set.of(CareNestRole.TEACHER));
        when(classes.findYearClassrooms(null)).thenReturn(List.of(cls(20L, "A"), cls(10L, "Z")));
        when(rules.today()).thenReturn(LocalDate.of(2026, 10, 28));
        when(rules.canRecord(eq(user), any())).thenReturn(true);
        service.list(user, EvaluationPeriodType.YEAR, null, null, null);
        var order = inOrder(classes);
        order.verify(classes).lockForUpdate(10L);
        order.verify(classes).lockForUpdate(20L);
    }

    private ClassroomInfoResponse cls(Long id, String name) {
        return new ClassroomInfoResponse(
                id,
                name,
                1L,
                1L,
                SchoolYearStatus.ACTIVE,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 5, 31),
                1L,
                List.of(),
                null,
                ClassroomStatus.ACTIVE);
    }
}
