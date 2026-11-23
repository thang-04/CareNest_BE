package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.entity.eduplan.EduContentThemeTick;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.entity.eduplan.EduTeamPlan;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.repository.eduplan.EduContentThemeTickRepository;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduTeamPlanRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.enums.EduPlanStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EduAiExampleServiceImplTest {
    private final EduTeamPlanRepository plans = mock(EduTeamPlanRepository.class);
    private final EduThemeRepository themes = mock(EduThemeRepository.class);
    private final EduPlanGoalRepository goals = mock(EduPlanGoalRepository.class);
    private final EduGoalContentRepository contents = mock(EduGoalContentRepository.class);
    private final EduContentThemeTickRepository ticks = mock(EduContentThemeTickRepository.class);
    private final SchoolStructureService structure = mock(SchoolStructureService.class);

    private EduAiExampleServiceImpl service(boolean enabled) {
        return new EduAiExampleServiceImpl(plans, themes, goals, contents, ticks, structure, enabled);
    }

    private YearPlanDetailResponse target(int count) {
        YearPlanDetailResponse target = mock(YearPlanDetailResponse.class);
        when(target.ageGroupId()).thenReturn(1L);
        List<YearPlanDetailResponse.Theme> list = new ArrayList<>();
        for (long i = 1; i <= count; i++)
            list.add(new YearPlanDetailResponse.Theme(i, "Gia đình", "GD", null, null, "", ""));
        when(target.themes()).thenReturn(list);
        return target;
    }

    private EduTeamPlan source(long id, long age, EduPlanStatus status, boolean disabled) {
        EduTeamPlan p = mock(EduTeamPlan.class);
        when(p.getId()).thenReturn(id);
        when(p.getAgeGroupId()).thenReturn(age);
        when(p.getStatus()).thenReturn(status);
        when(p.isDisabled()).thenReturn(disabled);
        return p;
    }

    private void data(long plan, int amount) {
        EduTheme t = mock(EduTheme.class);
        when(t.getId()).thenReturn(plan);
        when(t.getName()).thenReturn("Gia đình của bé");
        when(themes.findAllByYearPlanIdOrderBySortOrderAsc(plan)).thenReturn(List.of(t));
        EduPlanGoal goal = mock(EduPlanGoal.class);
        when(goal.getId()).thenReturn(plan);
        when(goal.getDomainId()).thenReturn(100L);
        when(goals.findAllByYearPlanIdOrderBySortOrderAsc(plan)).thenReturn(List.of(goal));
        List<EduGoalContent> lines = new ArrayList<>();
        List<EduContentThemeTick> pairs = new ArrayList<>();
        for (long i = 0; i < amount; i++) {
            EduGoalContent line = mock(EduGoalContent.class);
            when(line.getId()).thenReturn(plan * 100 + i);
            when(line.getGoalRowId()).thenReturn(plan);
            when(line.getText()).thenReturn("Nội dung " + plan + " " + i);
            lines.add(line);
            pairs.add(EduContentThemeTick.of(plan * 100 + i, plan));
        }
        when(contents.findAllByGoalRowIdInOrderBySortOrderAsc(java.util.Set.of(plan)))
                .thenReturn(lines);
        when(ticks.findAllByThemeIdIn(java.util.Set.of(plan))).thenReturn(pairs);
    }

    @Test
    void disabledByDefaultDoesNotReadSource() {
        assertThat(service(false).examples(target(1))).isEmpty();
        verifyNoInteractions(plans, themes, goals, contents, ticks, structure);
    }

    @Test
    void approvedSameAgeRangeComesFirstAndDraftDisabledAreNeverRead() {
        var service = service(true);
        when(structure.findAgeGroups())
                .thenReturn(List.of(
                        new AgeGroupResponse(1L, "", "", "", (short) 1, (short) 36, (short) 48),
                        new AgeGroupResponse(2L, "", "", "", (short) 1, (short) 36, (short) 48)));
        var otherAge = source(10, 3, EduPlanStatus.APPROVED, false);
        var sameAge = source(20, 2, EduPlanStatus.APPROVED, false);
        var draft = source(30, 1, EduPlanStatus.DRAFT, false);
        var disabled = source(40, 1, EduPlanStatus.APPROVED, true);
        when(plans.findApprovedForExamples()).thenReturn(List.of(otherAge, sameAge, draft, disabled));
        data(10, 6);
        data(20, 6);
        var result = service.examples(target(1));
        assertThat(result).hasSize(5).allMatch(e -> e.text().startsWith("Nội dung 20"));
        verify(themes, never()).findAllByYearPlanIdOrderBySortOrderAsc(30L);
        verify(themes, never()).findAllByYearPlanIdOrderBySortOrderAsc(40L);
    }

    @Test
    void totalBudgetIsSharedAcrossAllThemesAndDomains() {
        var service = service(true);
        var approved = source(20, 1, EduPlanStatus.APPROVED, false);
        when(plans.findApprovedForExamples()).thenReturn(List.of(approved));
        data(20, 10);
        var result = service.examples(target(20));
        assertThat(result).hasSize(60);
        assertThat(result.stream().collect(java.util.stream.Collectors.groupingBy(e -> e.themeId())))
                .allSatisfy((id, examples) -> assertThat(examples).hasSizeLessThanOrEqualTo(5));
    }

    @Test
    void noApprovedSourceAndUnrelatedNamesYieldNoExamples() {
        var service = service(true);
        assertThat(service.examples(target(1))).isEmpty();
        assertThat(EduAiExampleServiceImpl.near("GIA ĐÌNH", "Gia đình của bé")).isTrue();
        assertThat(EduAiExampleServiceImpl.near("", "Gia đình")).isFalse();
        assertThat(EduAiExampleServiceImpl.near("Giao thông", "Gia đình")).isFalse();
    }
}
