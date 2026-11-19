package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.entity.eduplan.EduPlanSubDomain;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduBreakRepository;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanExpectedResultRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduPlanSubDomainRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.EduGoalRowKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EduWorkflowPublicationTest {

    @Mock
    private SchoolStructureService schoolStructureService;

    @Mock
    private EduBreakRepository breakRepository;

    @Mock
    private EduThemeRepository themeRepository;

    @Mock
    private EduPlanSubDomainRepository subDomainRepository;

    @Mock
    private EduPlanExpectedResultRepository expectedResultRepository;

    @Mock
    private EduPlanGoalRepository goalRepository;

    @Mock
    private EduGoalContentRepository goalContentRepository;

    private YearPlanPublicationValidator validator;

    @Mock
    private com.carenest.repository.eduplan.EduPlanBranchTemplateRepository templateRepository;

    @BeforeEach
    void setUp() {
        validator = new YearPlanPublicationValidator(
                schoolStructureService,
                breakRepository,
                themeRepository,
                subDomainRepository,
                expectedResultRepository,
                goalRepository,
                goalContentRepository,
                templateRepository,
                new tools.jackson.databind.ObjectMapper());
    }

    private com.carenest.entity.eduplan.EduPlanBranchTemplate branch(long themeId, String start, String end) {
        return com.carenest.entity.eduplan.EduPlanBranchTemplate.create(
                1L,
                themeId,
                null,
                null,
                "Nhánh",
                start == null ? null : LocalDate.parse(start),
                end == null ? null : LocalDate.parse(end),
                (short) 0);
    }

    @Test
    @DisplayName("EDU-59: lịch nhánh phải bắt đầu Thứ Hai, kết thúc Thứ Sáu")
    void rejectsMisalignedBranchDates() {
        for (var branch : List.of(branch(1, "2026-09-08", "2026-09-11"), branch(1, "2026-09-07", "2026-09-12"))) {
            given(templateRepository.findAllByYearPlanIdOrderBySortOrderAsc(1L)).willReturn(List.of(branch));
            assertThatThrownBy(() -> validator.validateBranchDates(1L))
                    .isInstanceOf(GlobalException.class)
                    .hasFieldOrPropertyWithValue("apiCode", ApiCode.EDUCATION_CALENDAR_INVALID);
        }
    }

    @Test
    @DisplayName("EDU-59: từ chối nhánh trùng lịch trong cùng cha")
    void rejectsOverlappingSiblingBranches() {
        given(templateRepository.findAllByYearPlanIdOrderBySortOrderAsc(1L))
                .willReturn(List.of(branch(1, "2026-09-07", "2026-09-18"), branch(1, "2026-09-14", "2026-09-25")));
        assertThatThrownBy(() -> validator.validateBranchDates(1L))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.EDUCATION_CALENDAR_INVALID);
    }

    @Test
    @DisplayName("EDU-59: nhánh liền tuần, khác cha vẫn hợp lệ; nhánh thiếu ngày bị từ chối")
    void acceptsDisjointDifferentParentAndRejectsUndatedBranches() {
        given(templateRepository.findAllByYearPlanIdOrderBySortOrderAsc(1L))
                .willReturn(List.of(
                        branch(1, "2026-09-07", "2026-09-11"),
                        branch(1, "2026-09-14", "2026-09-18"),
                        branch(2, "2026-09-07", "2026-09-18")));
        assertThatCode(() -> validator.validateBranchDates(1L)).doesNotThrowAnyException();
        // Quy tắc hiện hành: mẫu nhánh phải có đủ ngày bắt đầu/kết thúc
        given(templateRepository.findAllByYearPlanIdOrderBySortOrderAsc(1L)).willReturn(List.of(branch(1, null, null)));
        assertThatThrownBy(() -> validator.validateBranchDates(1L))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.EDUCATION_CALENDAR_INVALID);
    }

    /** Kế hoạch năm cần tối thiểu 9 chủ đề; chủ đề phụ 1 tuần nối tiếp sau chủ đề chính. */
    private static List<EduTheme> withMinimumThemes(EduTheme first) {
        var themes = new java.util.ArrayList<EduTheme>(List.of(first));
        for (int i = 1; i < 9; i++) {
            EduTheme extra = mock(EduTheme.class);
            LocalDate monday = LocalDate.of(2026, 9, 7).plusWeeks(i);
            given(extra.getName()).willReturn("Chủ đề " + i);
            given(extra.getAbbr()).willReturn("CD" + i);
            given(extra.getStartDate()).willReturn(monday);
            given(extra.getEndDate()).willReturn(monday.plusDays(4));
            themes.add(extra);
        }
        return themes;
    }

    @Test
    @DisplayName("EDU-52: chủ đề thiếu ngày bắt đầu hoặc kết thúc thì từ chối ban hành (mã 7037)")
    void rejectsMissingThemeDates() {
        EduPlan plan = EduPlan.createDraft(
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                "P-01",
                1L,
                1L,
                "Kế hoạch năm",
                "Mô tả",
                10L,
                "TL",
                Instant.now());
        given(schoolStructureService.findSchoolYear(1L))
                .willReturn(Optional.of(
                        new SchoolYearResponse(1L, "2026-2027", LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31))));
        given(breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(List.of());

        EduTheme theme = mock(EduTheme.class);
        given(theme.getName()).willReturn("Trường mầm non");
        given(theme.getAbbr()).willReturn("TMN");
        given(theme.getStartDate()).willReturn(null);
        given(themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(List.of(theme));

        assertThatThrownBy(() -> validator.validateForPublication(plan))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.THEME_DATES_REQUIRED);
    }

    @Test
    @DisplayName(
            "EDU-53: lĩnh vực có lĩnh vực con nhưng có mục tiêu nằm ngoài lĩnh vực con thì từ chối ban hành (mã 7038)")
    void rejectsMixedSubDomainGoals() {
        EduPlan plan = EduPlan.createDraft(
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                "P-01",
                1L,
                1L,
                "Kế hoạch năm",
                "Mô tả",
                10L,
                "TL",
                Instant.now());
        given(schoolStructureService.findSchoolYear(1L))
                .willReturn(Optional.of(
                        new SchoolYearResponse(1L, "2026-2027", LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31))));
        given(breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(List.of());

        EduTheme theme = mock(EduTheme.class);
        given(theme.getName()).willReturn("Trường mầm non");
        given(theme.getAbbr()).willReturn("TMN");
        given(theme.getStartDate()).willReturn(LocalDate.of(2026, 9, 7)); // Monday
        given(theme.getEndDate()).willReturn(LocalDate.of(2026, 9, 11)); // Friday
        List<EduTheme> themes = withMinimumThemes(theme);
        given(themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(themes);

        EduPlanSubDomain sub = mock(EduPlanSubDomain.class);
        given(sub.getId()).willReturn(100L);
        given(sub.getDomainId()).willReturn(1L);
        given(sub.getSortOrder()).willReturn((short) 1);
        given(sub.getKind()).willReturn(EduGoalRowKind.SUB);
        given(sub.getText()).willReturn("Phát triển thể chất");

        EduPlanGoal goalWithoutSub = mock(EduPlanGoal.class);
        given(goalWithoutSub.getId()).willReturn(200L);
        given(goalWithoutSub.getDomainId()).willReturn(1L);
        given(goalWithoutSub.getSubDomainId()).willReturn(null);
        given(goalWithoutSub.getSortOrder()).willReturn((short) 2);
        given(goalWithoutSub.getKind()).willReturn(EduGoalRowKind.GOAL);
        given(goalWithoutSub.getText()).willReturn("Mục tiêu không có lĩnh vực con");

        given(subDomainRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(List.of(sub));
        given(expectedResultRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(List.of());
        given(goalRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(List.of(goalWithoutSub));

        assertThatThrownBy(() -> validator.validateForPublication(plan))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.GOAL_SUB_DOMAIN_MIXED);
    }

    @Test
    @DisplayName("EDU-51: trùng tên mục tiêu trong cùng phạm vi thì từ chối ban hành (mã 7036)")
    void rejectsDuplicateGoalNames() {
        EduPlan plan = EduPlan.createDraft(
                com.carenest.utils.enums.EduPlanLevel.SCHOOL_YEAR,
                "P-01",
                1L,
                1L,
                "Kế hoạch năm",
                "Mô tả",
                10L,
                "TL",
                Instant.now());
        given(schoolStructureService.findSchoolYear(1L))
                .willReturn(Optional.of(
                        new SchoolYearResponse(1L, "2026-2027", LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31))));
        given(breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(List.of());

        EduTheme theme = mock(EduTheme.class);
        given(theme.getName()).willReturn("Trường mầm non");
        given(theme.getAbbr()).willReturn("TMN");
        given(theme.getStartDate()).willReturn(LocalDate.of(2026, 9, 7)); // Monday
        given(theme.getEndDate()).willReturn(LocalDate.of(2026, 9, 11)); // Friday
        List<EduTheme> themes = withMinimumThemes(theme);
        given(themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(themes);

        EduPlanGoal goal1 = mock(EduPlanGoal.class);
        given(goal1.getId()).willReturn(201L);
        given(goal1.getDomainId()).willReturn(1L);
        given(goal1.getSubDomainId()).willReturn(null);
        given(goal1.getSortOrder()).willReturn((short) 1);
        given(goal1.getKind()).willReturn(EduGoalRowKind.GOAL);
        given(goal1.getText()).willReturn("Mục tiêu A");

        EduPlanGoal goal2 = mock(EduPlanGoal.class);
        given(goal2.getId()).willReturn(202L);
        given(goal2.getDomainId()).willReturn(1L);
        given(goal2.getSubDomainId()).willReturn(null);
        given(goal2.getSortOrder()).willReturn((short) 2);
        given(goal2.getKind()).willReturn(EduGoalRowKind.GOAL);
        given(goal2.getText()).willReturn("mục tiêu a");

        given(subDomainRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(List.of());
        given(expectedResultRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(List.of());
        given(goalRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .willReturn(List.of(goal1, goal2));
        given(goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(org.mockito.ArgumentMatchers.anySet()))
                .willReturn(List.of());

        assertThatThrownBy(() -> validator.validateForPublication(plan))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.GOAL_NAME_DUPLICATE);
    }
}
