package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.service.eduplan.EduPreviousYearContentService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.enums.EduPlanLevel;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EduPreviousYearContentServiceImplTest {

    private final SchoolStructureService structure = mock(SchoolStructureService.class);
    private final EduPlanRepository plans = mock(EduPlanRepository.class);
    private final EduPlanGoalRepository goals = mock(EduPlanGoalRepository.class);
    private final EduGoalContentRepository contents = mock(EduGoalContentRepository.class);
    private final EduPreviousYearContentServiceImpl service =
            new EduPreviousYearContentServiceImpl(structure, plans, goals, contents);

    private EduPlan plan(long id, EduPlanStatus status, int version) {
        EduPlan p = mock(EduPlan.class);
        when(p.getId()).thenReturn(id);
        when(p.getStatus()).thenReturn(status);
        when(p.getVersionNo()).thenReturn(version);
        return p;
    }

    private EduGoalContent content(long id, long rowId, String text) {
        EduGoalContent c = mock(EduGoalContent.class);
        when(c.getId()).thenReturn(id);
        when(c.getGoalRowId()).thenReturn(rowId);
        when(c.getText()).thenReturn(text);
        return c;
    }

    @Test
    @DisplayName("EDU-106: nội dung kế hoạch năm học APPROVED mới nhất năm liền trước; không có thì rỗng")
    void previousYearContentsReadsLatestApprovedSchoolYearPlan() {
        when(structure.findSchoolYears())
                .thenReturn(List.of(
                        new SchoolYearResponse(2L, "2025-2026", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31)),
                        new SchoolYearResponse(3L, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 5, 31))));
        EduPlan v1 = plan(61L, EduPlanStatus.APPROVED, 1);
        EduPlan v2 = plan(62L, EduPlanStatus.APPROVED, 2);
        EduPlan draft = plan(63L, EduPlanStatus.DRAFT, 3);
        when(plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(EduPlanLevel.SCHOOL_YEAR, 2L, 5L))
                .thenReturn(List.of(v1, v2, draft));
        EduPlanGoal row = mock(EduPlanGoal.class);
        when(row.getId()).thenReturn(30L);
        when(row.getDomainId()).thenReturn(4L);
        when(row.getText()).thenReturn("Trẻ hát");
        when(goals.findAllByYearPlanIdOrderBySortOrderAsc(62L)).thenReturn(List.of(row));
        List<EduGoalContent> rows = List.of(content(70L, 30L, " Hát dân ca "), content(71L, 30L, " "));
        when(contents.findAllByGoalRowIdInOrderBySortOrderAsc(any())).thenReturn(rows);

        assertThat(service.previousYearContents(3L, 5L))
                .containsExactly(new EduPreviousYearContentService.PreviousContent(4L, "Trẻ hát", "Hát dân ca"));
        // Năm đầu tiên không có năm trước
        assertThat(service.previousYearContents(2L, 5L)).isEmpty();
    }
}
