package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.entity.eduplan.EduPlanBranchTemplate;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.utils.enums.EduGoalRowKind;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduBreakRepository;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanBranchTemplateRepository;
import com.carenest.repository.eduplan.EduPlanExpectedResultRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduPlanSubDomainRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.service.organization.SchoolStructureService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class YearPlanPublicationValidatorTest {
    @Test
    void annualGoalsNeedContentAndAtLeastOneExpectedResultChain() {
        var group = mock(EduGoalRow.class);
        var first = mock(EduGoalRow.class);
        var second = mock(EduGoalRow.class);
        when(group.getKind()).thenReturn(EduGoalRowKind.GROUP);
        when(group.getId()).thenReturn(1L);
        when(group.getText()).thenReturn("Kết quả");
        when(first.getKind()).thenReturn(EduGoalRowKind.GOAL);
        when(first.getId()).thenReturn(2L);
        when(first.getText()).thenReturn("Mục tiêu 1");
        when(first.getGoalNo()).thenReturn((short) 1);
        when(first.getExpectedResultId()).thenReturn(1L);
        when(second.getKind()).thenReturn(EduGoalRowKind.GOAL);
        when(second.getId()).thenReturn(3L);
        when(second.getText()).thenReturn("Mục tiêu 2");
        var content = EduGoalContent.create();
        content.place(2L, (short) 0, "Nội dung");
        assertThatThrownBy(() -> YearPlanPublicationValidator.requireAnnualContents(
                List.of(group, first, second), List.of(content)))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getFieldErrors().getFirst().message())
                        .contains("Mục tiêu 2"));
        var another = EduGoalContent.create();
        another.place(3L, (short) 0, "Nội dung khác");
        YearPlanPublicationValidator.requireAnnualContents(List.of(group, first, second), List.of(content, another));
        assertThatThrownBy(() -> YearPlanPublicationValidator.requireAnnualContents(
                List.of(first, second), List.of(content, another)))
                .isInstanceOf(GlobalException.class);
    }
    @Test
    void submissionChecksSavedDurationLimitAndRejectsUnknownSnapshot() {
        var themes = mock(EduThemeRepository.class);
        var breaks = mock(EduBreakRepository.class);
        var templates = mock(EduPlanBranchTemplateRepository.class);
        var validator = new YearPlanPublicationValidator(
                mock(SchoolStructureService.class), breaks, themes, mock(EduPlanSubDomainRepository.class),
                mock(EduPlanExpectedResultRepository.class), mock(EduPlanGoalRepository.class),
                mock(EduGoalContentRepository.class), templates, new tools.jackson.databind.ObjectMapper());
        var plan = mock(EduPlan.class);
        var theme = mock(EduTheme.class);
        when(plan.getId()).thenReturn(42L);
        when(plan.getDurationPolicySnapshot()).thenReturn(
                "{\"mode\":\"BOUNDED\",\"theme\":{\"minWeeks\":2,\"maxWeeks\":4},"
                        + "\"branch\":{\"minWeeks\":1,\"maxWeeks\":2}}");
        when(theme.getName()).thenReturn("Chủ đề A");
        when(theme.getStartDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(theme.getEndDate()).thenReturn(LocalDate.of(2026, 10, 9));
        when(themes.findAllByYearPlanIdOrderBySortOrderAsc(42L)).thenReturn(List.of(theme));
        assertThatThrownBy(() -> validator.validateSubmissionSchedule(plan))
                .isInstanceOf(GlobalException.class).hasMessageContaining("Chủ đề A");
        when(plan.getDurationPolicySnapshot()).thenReturn("{\"mode\":\"MISSING_OR_UNKNOWN\"}");
        assertThatThrownBy(() -> validator.validateSubmissionSchedule(plan))
                .isInstanceOf(GlobalException.class).hasMessageContaining("giới hạn");
        when(plan.getTitle()).thenReturn("Năm học cần kiểm tra");
        assertThatThrownBy(() -> validator.validateForPublication(plan))
                .isInstanceOf(GlobalException.class).hasMessageContaining("giới hạn");
    }

    @Test
    void legacyBranchStillMustFitItsParentTheme() {
        var themes = mock(EduThemeRepository.class);
        var templates = mock(EduPlanBranchTemplateRepository.class);
        var validator = new YearPlanPublicationValidator(
                mock(SchoolStructureService.class), mock(EduBreakRepository.class), themes,
                mock(EduPlanSubDomainRepository.class), mock(EduPlanExpectedResultRepository.class),
                mock(EduPlanGoalRepository.class), mock(EduGoalContentRepository.class), templates,
                new tools.jackson.databind.ObjectMapper());
        var plan = mock(EduPlan.class);
        var theme = mock(EduTheme.class);
        var branch = mock(EduPlanBranchTemplate.class);
        when(plan.getId()).thenReturn(42L);
        when(plan.getDurationPolicySnapshot()).thenReturn("{\"mode\":\"LEGACY_UNBOUNDED\"}");
        when(theme.getId()).thenReturn(7L);
        when(theme.getStartDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(theme.getEndDate()).thenReturn(LocalDate.of(2026, 9, 18));
        when(branch.getThemeId()).thenReturn(7L);
        when(branch.getName()).thenReturn("Nhánh");
        when(branch.getStartDate()).thenReturn(LocalDate.of(2026, 9, 14));
        when(branch.getEndDate()).thenReturn(LocalDate.of(2026, 9, 25));
        when(themes.findAllByYearPlanIdOrderBySortOrderAsc(42L)).thenReturn(List.of(theme));
        when(templates.findAllByYearPlanIdOrderBySortOrderAsc(42L)).thenReturn(List.of(branch));
        assertThatThrownBy(() -> validator.validateSubmissionSchedule(plan))
                .isInstanceOf(GlobalException.class).hasMessageContaining("ngoài chủ đề");
    }
    @Test
    void annualSubmissionRequiresAtLeastNineThemes() {
        assertThatThrownBy(() -> YearPlanPublicationValidator.requireMinimumThemes(8))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("Chưa đạt số lượng chủ đề cho phép");
        YearPlanPublicationValidator.requireMinimumThemes(9);
    }
    private YearPlanPublicationValidator validator(EduPlanBranchTemplateRepository templates) {
        return new YearPlanPublicationValidator(
                mock(SchoolStructureService.class), mock(EduBreakRepository.class), mock(EduThemeRepository.class),
                mock(EduPlanSubDomainRepository.class), mock(EduPlanExpectedResultRepository.class),
                mock(EduPlanGoalRepository.class), mock(EduGoalContentRepository.class), templates,
                mock(tools.jackson.databind.ObjectMapper.class));
    }

    @Test
    void declaredBranchWithoutEndDateCannotBeSubmitted() {
        var templates = mock(EduPlanBranchTemplateRepository.class);
        var branch = mock(EduPlanBranchTemplate.class);
        when(branch.getName()).thenReturn("Nhánh một");
        when(branch.getStartDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(templates.findAllByYearPlanIdOrderBySortOrderAsc(42L)).thenReturn(List.of(branch));
        assertThatThrownBy(() -> validator(templates).validateBranchDates(42L))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("Nhánh một");
    }

    @Test
    void optionalBranchCanBeAbsentAndCompleteBranchCanBeSubmitted() {
        var templates = mock(EduPlanBranchTemplateRepository.class);
        var branch = mock(EduPlanBranchTemplate.class);
        when(branch.getStartDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(branch.getEndDate()).thenReturn(LocalDate.of(2026, 9, 11));
        when(templates.findAllByYearPlanIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        when(templates.findAllByYearPlanIdOrderBySortOrderAsc(2L)).thenReturn(List.of(branch));
        assertThat(validator(templates).validateBranchDates(1L)).isEmpty();
        assertThat(validator(templates).validateBranchDates(2L)).containsExactly(branch);
    }

    @Test
    void themeScheduleGapThrowsException() {
        var t1 = mock(EduTheme.class);
        when(t1.getStartDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(t1.getEndDate()).thenReturn(LocalDate.of(2026, 9, 18));
        var t2 = mock(EduTheme.class);
        when(t2.getStartDate()).thenReturn(LocalDate.of(2026, 9, 28));
        when(t2.getEndDate()).thenReturn(LocalDate.of(2026, 10, 9));

        assertThatThrownBy(() -> EduYearPlanServiceImpl.requireNoThemeGaps(List.of(t1, t2), List.of()))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("Bị trống ngày chưa tạo chủ đề")
                .hasMessageContaining("21/09/2026");
    }
}
