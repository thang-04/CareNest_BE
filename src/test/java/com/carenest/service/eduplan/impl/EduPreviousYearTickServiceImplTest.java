package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.PreviousYearTicksResponse;
import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.eduplan.EduContentThemeTick;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduContentThemeTickRepository;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanExpectedResultRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.repository.eduplan.EduPlanSubDomainRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.EduPlanLevel;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EduPreviousYearTickServiceImplTest {
    private final EduYearPlanService yearPlans = mock(EduYearPlanService.class);
    private final SchoolStructureService structure = mock(SchoolStructureService.class);
    private final EduPlanRepository plans = mock(EduPlanRepository.class);
    private final EduThemeRepository themes = mock(EduThemeRepository.class);
    private final EduPlanSubDomainRepository subDomains = mock(EduPlanSubDomainRepository.class);
    private final EduPlanExpectedResultRepository expected = mock(EduPlanExpectedResultRepository.class);
    private final EduPlanGoalRepository goals = mock(EduPlanGoalRepository.class);
    private final EduGoalContentRepository contents = mock(EduGoalContentRepository.class);
    private final EduContentThemeTickRepository ticks = mock(EduContentThemeTickRepository.class);
    private final CurrentUser user = mock(CurrentUser.class);
    private EduPreviousYearTickServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EduPreviousYearTickServiceImpl(
                yearPlans, structure, plans, themes, subDomains, expected, goals, contents, ticks);
        when(user.userId()).thenReturn(7L);
        when(structure.findSchoolYears())
                .thenReturn(List.of(
                        new SchoolYearResponse(1L, "2024-2025", LocalDate.of(2024, 9, 1), LocalDate.of(2025, 5, 31)),
                        new SchoolYearResponse(2L, "2025-2026", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 5, 31)),
                        new SchoolYearResponse(3L, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 5, 31))));
    }

    private void current(EduPlanLevel level, boolean canAssign, Long schoolYearId) {
        YearPlanDetailResponse detail = mock(YearPlanDetailResponse.class);
        when(detail.planLevel()).thenReturn(level);
        when(detail.canAssignContents()).thenReturn(canAssign);
        when(detail.schoolYearId()).thenReturn(schoolYearId);
        when(detail.ageGroupId()).thenReturn(5L);
        when(yearPlans.getYearPlan(user, 100L)).thenReturn(detail);
    }

    private EduPlan plan(long id, EduPlanStatus status, int version) {
        EduPlan p = mock(EduPlan.class);
        when(p.getId()).thenReturn(id);
        when(p.getStatus()).thenReturn(status);
        when(p.getVersionNo()).thenReturn(version);
        return p;
    }

    private EduTheme theme(long id, String name) {
        EduTheme t = mock(EduTheme.class);
        when(t.getId()).thenReturn(id);
        when(t.getName()).thenReturn(name);
        return t;
    }

    private EduGoalContent content(long id, long rowId, String text) {
        EduGoalContent c = mock(EduGoalContent.class);
        when(c.getId()).thenReturn(id);
        when(c.getGoalRowId()).thenReturn(rowId);
        when(c.getText()).thenReturn(text);
        return c;
    }

    @Test
    @DisplayName("EDU-104: lấy bản APPROVED versionNo lớn nhất của năm liền trước, tick chỉ của chủ đề nguồn, bỏ chữ trống")
    void readsLatestApprovedOfPreviousYear() {
        current(EduPlanLevel.TEAM, true, 3L);
        EduPlan draft = plan(50L, EduPlanStatus.DRAFT, 4);
        EduPlan v1 = plan(51L, EduPlanStatus.APPROVED, 1);
        EduPlan v2 = plan(52L, EduPlanStatus.APPROVED, 2);
        when(plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(EduPlanLevel.TEAM, 2L, 5L))
                .thenReturn(List.of(v1, v2, draft));
        List<EduTheme> sourceThemes = List.of(theme(9L, "Trường mầm non"), theme(10L, "Gia đình"));
        when(themes.findAllByYearPlanIdOrderBySortOrderAsc(52L)).thenReturn(sourceThemes);
        EduPlanGoal row = mock(EduPlanGoal.class);
        when(row.getId()).thenReturn(30L);
        when(row.getDomainId()).thenReturn(4L);
        when(goals.findAllByYearPlanIdOrderBySortOrderAsc(52L)).thenReturn(List.of(row));
        List<EduGoalContent> sourceContents = List.of(content(70L, 30L, " Bé chào cô "), content(71L, 30L, "  "));
        when(contents.findAllByGoalRowIdInOrderBySortOrderAsc(any())).thenReturn(sourceContents);
        when(ticks.findAllByThemeIdIn(any()))
                .thenReturn(List.of(
                        EduContentThemeTick.of(70L, 9L),
                        EduContentThemeTick.of(71L, 9L),
                        EduContentThemeTick.of(999L, 10L)));

        PreviousYearTicksResponse out = service.getPreviousYearTicks(user, 100L);

        assertThat(out.reason()).isNull();
        assertThat(out.source().planId()).isEqualTo(52L);
        assertThat(out.source().schoolYearId()).isEqualTo(2L);
        assertThat(out.themes()).extracting(PreviousYearTicksResponse.Theme::name)
                .containsExactly("Trường mầm non", "Gia đình");
        assertThat(out.ticks()).containsExactly(new PreviousYearTicksResponse.Tick(4L, "Bé chào cô", 9L));
    }

    @Test
    @DisplayName("EDU-104: không phải kế hoạch tổ hoặc không được tick ⇒ 7005, không đọc nguồn")
    void rejectsWithoutAssignRight() {
        current(EduPlanLevel.TEAM, false, 3L);
        assertThatThrownBy(() -> service.getPreviousYearTicks(user, 100L))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.YEAR_PLAN_EDIT_FORBIDDEN));
        current(EduPlanLevel.SCHOOL_YEAR, true, 3L);
        assertThatThrownBy(() -> service.getPreviousYearTicks(user, 100L)).isInstanceOf(GlobalException.class);
        verify(plans, never()).findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(any(), any(), any());
        verify(ticks, never()).findAllByThemeIdIn(any());
    }

    @Test
    @DisplayName("EDU-104: không có năm trước ⇒ NO_PREVIOUS_YEAR; năm trước không có bản APPROVED ⇒ NO_APPROVED_PLAN")
    void reportsMissingSource() {
        current(EduPlanLevel.TEAM, true, 1L);
        PreviousYearTicksResponse none = service.getPreviousYearTicks(user, 100L);
        assertThat(none.source()).isNull();
        assertThat(none.reason()).isEqualTo(PreviousYearTicksResponse.NO_PREVIOUS_YEAR);

        current(EduPlanLevel.TEAM, true, 3L);
        EduPlan review = plan(60L, EduPlanStatus.REVIEW, 1);
        when(plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(eq(EduPlanLevel.TEAM), eq(2L), anyLong()))
                .thenReturn(List.of(review));
        when(structure.findAgeGroup(5L)).thenReturn(Optional.empty());
        PreviousYearTicksResponse missing = service.getPreviousYearTicks(user, 100L);
        assertThat(missing.source()).isNull();
        assertThat(missing.reason()).isEqualTo(PreviousYearTicksResponse.NO_APPROVED_PLAN);
        assertThat(missing.ticks()).isEmpty();
    }
}
