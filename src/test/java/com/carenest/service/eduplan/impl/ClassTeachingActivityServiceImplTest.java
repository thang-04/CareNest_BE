package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClassTeachingActivityServiceImplTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final CurrentUser TEACHER =
            new CurrentUser(5L, "kc-5", "teacher.it", "GV", null, Set.of(CareNestRole.TEACHER));

    private final ClassroomAccessPolicy access = mock(ClassroomAccessPolicy.class);
    private final EduLessonQueryService lessons = mock(EduLessonQueryService.class);
    private final ClassTeachingActivityServiceImpl service = new ClassTeachingActivityServiceImpl(
            access, lessons, Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

    @Test
    void returnsMondayToFridayWithOnlyReleasedDays() {
        var activity = new ApprovedDayLessonResponse.Activity("hoc", "Hoạt động học", "Vẽ", List.of());
        when(lessons.findReleasedDayActivities(1L, MONDAY, MONDAY.plusDays(4)))
                .thenReturn(Map.of(
                        MONDAY.plusDays(1),
                        new ApprovedDayLessonResponse(9L, MONDAY, MONDAY.plusDays(1), "Tuần 1", List.of(activity))));

        var week = service.getWeek(TEACHER, 1L, null);

        assertThat(week.weekStart()).isEqualTo(MONDAY);
        assertThat(week.published()).isTrue();
        assertThat(week.days()).extracting("published").containsExactly(false, true, false, false, false);
        assertThat(week.days().get(1).activities()).containsExactly(activity);
    }

    @Test
    void teacherOfAnotherClassIsRejectedBeforeReadingPlans() {
        doThrow(GlobalException.of(ApiCode.CLASS_ACCESS_DENIED)).when(access).checkCanViewClassroom(TEACHER, 2L);

        assertThatThrownBy(() -> service.getWeek(TEACHER, 2L, MONDAY)).isInstanceOf(GlobalException.class);
        verifyNoInteractions(lessons);
    }
}
