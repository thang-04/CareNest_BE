package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.ApprovedDayLessonResponse;
import com.carenest.dto.eduplan.ApprovedDayLessonResponse.Activity;
import com.carenest.dto.eduplan.ApprovedDayLessonResponse.Goal;
import com.carenest.entity.observation.DailyActivityResult;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.utils.enums.Participation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssessmentResultProjectionTest {
    @Test
    void eachDayAndActivityUsesItsOwnGoalIdsWithOneLineageLookup() {
        EduLessonQueryService query = mock(EduLessonQueryService.class);
        when(query.findGoalLineages(anyCollection())).thenReturn(Map.of(1L, 1L, 11L, 1L, 2L, 2L));
        LocalDate first = LocalDate.of(2026, 10, 28);
        LocalDate second = first.plusDays(1);
        Instant at = Instant.parse("2026-10-29T01:00:00Z");
        DailyActivityResult oldDay = DailyActivityResult.start(1L, 10L, 100L, first, "hoc", 3L, at);
        oldDay.record(20L, Participation.TICH_CUC, List.of(1L), List.of(), List.of());
        DailyActivityResult newDay = DailyActivityResult.start(1L, 10L, 100L, second, "hoc", 3L, at);
        newDay.record(20L, Participation.TICH_CUC, List.of(1L), List.of(), List.of(2L));
        ApprovedDayLessonResponse oldLesson = new ApprovedDayLessonResponse(
                20L,
                first,
                first,
                "V1",
                List.of(new Activity("hoc", "Học", "", List.of(new Goal(1L, (short) 1, "Goal")))));
        ApprovedDayLessonResponse newLesson = new ApprovedDayLessonResponse(
                21L,
                first,
                second,
                "V2",
                List.of(
                        new Activity("hoc", "Học", "", List.of(new Goal(11L, (short) 1, "Goal"))),
                        new Activity("goc", "Góc", "", List.of(new Goal(2L, (short) 2, "Other")))));

        var projected = AssessmentResultProjection.project(
                query, Map.of(first, oldLesson, second, newLesson), List.of(oldDay, newDay));

        assertThat(projected.get(oldDay).goalsMet()).containsExactly(1L);
        assertThat(projected.get(newDay).goalsMet()).containsExactly(11L);
        assertThat(projected.get(newDay).goalsNotMet()).isEmpty();
        assertThat(projected.get(newDay).participation()).isEqualTo(Participation.TICH_CUC);
        assertThat(newDay.getGoalsMet()).containsExactly(1L);
        assertThat(newDay.getGoalsNotMet()).containsExactly(2L);
        assertThat(newDay.getClassDocId()).isEqualTo(20L);
        verify(query, times(1)).findGoalLineages(anyCollection());
    }
}
