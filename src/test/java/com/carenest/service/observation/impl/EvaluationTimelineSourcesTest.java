package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.DailyActivityResult;
import com.carenest.entity.observation.DailyObservation;
import com.carenest.repository.observation.DailyActivityResultRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.Participation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EvaluationTimelineSourcesTest {
    @Test
    void oldGoalsUseOriginalClassLessonsEvenIfNewClassHasDifferentLessonOnSameDay() {
        var attendance = mock(AttendanceService.class);
        var observations = mock(DailyObservationRepository.class);
        var activities = mock(DailyActivityResultRepository.class);
        var health = mock(DailyHealthService.class);
        var lessons = mock(EduLessonQueryService.class);
        var classes = mock(ClassroomService.class);
        var oldClass = classroom(1L, "Class A");
        when(oldClass.mealSessions()).thenReturn(List.of(MealSession.MORNING, MealSession.LUNCH));
        var newClass = classroom(2L, "Class B");
        var oldDay = LocalDate.of(2026, 10, 13);
        var newDay = LocalDate.of(2026, 10, 15);
        var ids = List.of(10L);
        var oldObservation = mock(DailyObservation.class);
        when(oldObservation.getChildId()).thenReturn(10L);
        when(oldObservation.getClassroomId()).thenReturn(1L);
        when(oldObservation.getDate()).thenReturn(oldDay);
        when(oldObservation.getMeals()).thenReturn("{\"MORNING\":\"AN_HET\"}");
        when(observations.findByChildIdInAndDateBetween(ids, oldDay, newDay)).thenReturn(List.of(oldObservation));
        when(classes.findClassroomInfo(1L)).thenReturn(Optional.of(oldClass));
        when(activities.findByChildIdInAndDateBetween(ids, oldDay, newDay))
                .thenReturn(List.of(result(1L, oldDay, 1001L), result(2L, newDay, 2001L)));
        when(attendance.findDayStatuses(ids, oldDay, newDay))
                .thenReturn(List.of(
                        new AttendanceService.ChildDayStatus(10L, oldDay, AttendanceStatus.PRESENT),
                        new AttendanceService.ChildDayStatus(10L, newDay, AttendanceStatus.PRESENT)));
        when(lessons.findApprovedDayLessons(1L, oldDay, newDay))
                .thenReturn(Map.of(oldDay, lesson(oldDay, 1001L, "Original goal")));
        when(lessons.findApprovedDayLessons(2L, oldDay, newDay))
                .thenReturn(Map.of(
                        oldDay, lesson(oldDay, 2001L, "Different new-class goal"),
                        newDay, lesson(newDay, 2001L, "New goal")));
        when(lessons.findGoalLineages(anyCollection())).thenReturn(Map.of(1001L, 1001L, 2001L, 2001L));
        var sources = new EvaluationSources(
                attendance, observations, activities, health, lessons, new ObjectMapper(), classes);
        var data = sources.loadTimeline(newClass, 10L, oldDay, newDay).get(10L);
        var days = data.days();
        assertThat(data.structured().meals().get(MealSession.MORNING).get(com.carenest.utils.enums.EatingLevel.AN_HET))
                .isEqualTo(1);
        assertThat(days).hasSize(2);
        assertThat(days.getFirst().met()).extracting(g -> g.id()).containsExactly(1001L);
        assertThat(days.getFirst().met().getFirst().description()).isEqualTo("Original goal");
        assertThat(days.getFirst().className()).isEqualTo("Class A");
        assertThat(days.getLast().className()).isEqualTo("Class B");
        assertThat(days.getLast().met()).extracting(g -> g.id()).containsExactly(2001L);
    }

    private ClassroomInfoResponse classroom(Long id, String name) {
        var cls = mock(ClassroomInfoResponse.class);
        when(cls.id()).thenReturn(id);
        when(cls.name()).thenReturn(name);
        when(cls.mealSessions()).thenReturn(List.of(MealSession.LUNCH));
        return cls;
    }

    private DailyActivityResult result(Long cls, LocalDate day, Long goal) {
        var result = DailyActivityResult.start(10L, cls, 1L, day, "hoc", 1L, Instant.parse("2026-10-21T00:00:00Z"));
        result.record(1L, Participation.BINH_THUONG, List.of(goal), List.of(), List.of());
        return result;
    }

    private ApprovedDayLessonResponse lesson(LocalDate day, Long goal, String name) {
        return new ApprovedDayLessonResponse(
                1L,
                day,
                day,
                "Week",
                List.of(new ApprovedDayLessonResponse.Activity(
                        "hoc",
                        "Activity",
                        "Topic",
                        List.of(new ApprovedDayLessonResponse.Goal(goal, (short) 1, name)))));
    }
}
