package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.utils.enums.EduClassDocStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EduLessonQueryServiceImplTest {
    private final EduClassDocRepository docs = mock(EduClassDocRepository.class);
    private final EduPlanGoalRepository goals = mock(EduPlanGoalRepository.class);
    private final EduConfigService config = mock(EduConfigService.class);
    private final EduLessonQueryServiceImpl service =
            new EduLessonQueryServiceImpl(docs, goals, config, new ObjectMapper());

    @Test
    void observationsKeepOldApprovedLessonUntilReplacementLessonIsApproved() {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        EduClassDoc old = week(100L, EduClassDocStatus.APPROVED, monday);
        EduClassDoc update = mock(EduClassDoc.class);
        when(update.getKind()).thenReturn(EduClassDoc.WEEK);
        when(update.getStatus()).thenReturn(EduClassDocStatus.REVIEW, EduClassDocStatus.APPROVED);
        when(update.getWeekStart()).thenReturn(monday);
        when(update.getId()).thenReturn(200L);
        when(update.getContent()).thenReturn("{}");
        EduClassDoc releasedDay = day(EduClassDocStatus.APPROVED, "[]", monday);
        when(docs.findAllByClassroomIdInOrderByIdAsc(List.of(1L))).thenReturn(List.of(old, update, releasedDay));
        when(config.getEduConfig()).thenReturn(new EduConfigResponse(List.of(), List.of(), List.of(), null, List.of(), null));
        when(goals.findAllById(Set.of())).thenReturn(List.of());

        assertThat(service.findApprovedDayLessons(1L, monday, monday).get(monday).classDocId()).isEqualTo(100L);
        assertThat(service.findApprovedDayLessons(1L, monday, monday).get(monday).classDocId()).isEqualTo(200L);
    }

    @Test
    void lessonNeedsReleasedDayPlanOfTheWeek() {
        // Spec 003 FR-008: kế hoạch tuần ban hành trong gói học kỳ nhưng giáo án ngày chưa duyệt ⇒ chưa chấm
        LocalDate monday = LocalDate.of(2026, 11, 9);
        when(config.getEduConfig()).thenReturn(new EduConfigResponse(List.of(), List.of(), List.of(), null, List.of(), null));
        when(goals.findAllById(Set.of())).thenReturn(List.of());
        EduClassDoc approvedWeek = week(100L, EduClassDocStatus.APPROVED, monday);
        EduClassDoc draftDay = day(EduClassDocStatus.DRAFT, "[]", monday);
        when(docs.findAllByClassroomIdInOrderByIdAsc(List.of(1L))).thenReturn(List.of(approvedWeek, draftDay));
        assertThat(service.findApprovedDayLessons(1L, monday, monday)).isEmpty();
        // Hồ sơ đã ban hành rồi mở lại sửa phần tương lai: ngày đã dạy vẫn chấm được
        EduClassDoc reopenedDay = day(EduClassDocStatus.DRAFT, "[{\"action\":\"revise_future\"}]", monday);
        when(docs.findAllByClassroomIdInOrderByIdAsc(List.of(1L))).thenReturn(List.of(approvedWeek, reopenedDay));
        assertThat(service.findApprovedDayLessons(1L, monday, monday)).containsKey(monday);
    }

    private static EduClassDoc day(EduClassDocStatus status, String history, LocalDate monday) {
        EduClassDoc doc = mock(EduClassDoc.class);
        when(doc.getKind()).thenReturn(EduClassDoc.DAY);
        when(doc.getStatus()).thenReturn(status);
        when(doc.getHistory()).thenReturn(history);
        when(doc.getWeekStart()).thenReturn(monday);
        return doc;
    }

    @Test
    void goalIdsKeepLineageMappingAcrossTeamUpdates() {
        EduPlanGoal goal = mock(EduPlanGoal.class);
        when(goal.getId()).thenReturn(300L);
        when(goal.lineage()).thenReturn(30L);
        when(goals.findAllById(List.of(300L))).thenReturn(List.of(goal));

        assertThat(service.findGoalLineages(List.of(300L))).containsEntry(300L, 30L);
        assertThat(service.findGoalLineages(List.of())).isEmpty();
    }

    private static EduClassDoc week(Long id, EduClassDocStatus status, LocalDate monday) {
        EduClassDoc doc = mock(EduClassDoc.class);
        when(doc.getKind()).thenReturn(EduClassDoc.WEEK);
        when(doc.getStatus()).thenReturn(status);
        when(doc.getWeekStart()).thenReturn(monday);
        when(doc.getId()).thenReturn(id);
        when(doc.getContent()).thenReturn("{}");
        return doc;
    }
}
