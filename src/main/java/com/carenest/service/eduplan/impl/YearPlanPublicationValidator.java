package com.carenest.service.eduplan.impl;

import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.eduplan.EduBreak;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduYearPlan;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Điều kiện kiểm tra trước khi ban hành kế hoạch giáo dục năm học (EDU-51, EDU-52, EDU-53 và các ràng buộc sẵn sàng).
 * Dùng chung cho luồng ban hành thủ công và ban hành tự động do đổi policy (EDU-54).
 */
@Component
@RequiredArgsConstructor
public class YearPlanPublicationValidator {

    private final SchoolStructureService schoolStructureService;
    private final EduBreakRepository breakRepository;
    private final EduThemeRepository themeRepository;
    private final EduPlanSubDomainRepository subDomainRepository;
    private final EduPlanExpectedResultRepository expectedResultRepository;
    private final EduPlanGoalRepository goalRepository;
    private final EduGoalContentRepository goalContentRepository;

    public void validateForPublication(EduYearPlan plan) {
        if (plan.getTitle() == null || plan.getTitle().isBlank()) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_TITLE_REQUIRED);
        }

        SchoolYearResponse year = schoolStructureService
                .findSchoolYear(plan.getSchoolYearId())
                .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));

        List<EduBreak> breaks = breakRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
        for (EduBreak item : breaks) {
            if (item.getStartDate() != null
                    && item.getEndDate() != null
                    && item.getEndDate().isBefore(item.getStartDate())) {
                throw GlobalException.of(ApiCode.BREAK_END_BEFORE_START);
            }
        }

        List<EduTheme> themes = themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId());
        Set<String> abbrs = new HashSet<>();
        for (EduTheme theme : themes) {
            if (theme.getName() == null
                    || theme.getName().isBlank()
                    || theme.getAbbr() == null
                    || theme.getAbbr().isBlank()) {
                throw GlobalException.of(ApiCode.THEME_NAME_REQUIRED);
            }
            if (!abbrs.add(theme.getAbbr().trim().toLowerCase(Locale.ROOT))) {
                throw GlobalException.of(ApiCode.THEME_ABBR_DUPLICATE);
            }
            if (theme.getStartDate() != null
                    && theme.getEndDate() != null
                    && theme.getEndDate().isBefore(theme.getStartDate())) {
                throw GlobalException.of(ApiCode.THEME_END_BEFORE_START);
            }
            // EDU-52: ban hành cần mọi chủ đề có đủ ngày bắt đầu, kết thúc
            if (theme.getStartDate() == null || theme.getEndDate() == null) {
                throw GlobalException.of(ApiCode.THEME_DATES_REQUIRED, theme.getAbbr());
            }
        }

        requireThemeWeeks(themes);
        requireDatesInSchoolYear(year, themes, breaks);

        List<EduGoalRow> rows = findRows(plan.getId());
        boolean hasGoal = rows.stream().anyMatch(row -> row.getKind() == EduGoalRowKind.GOAL);
        if (!hasGoal) {
            throw GlobalException.of(ApiCode.PLAN_GOAL_REQUIRED);
        }
        if (rows.stream()
                .anyMatch(row -> row.getKind() == EduGoalRowKind.GOAL
                        && (row.getText() == null || row.getText().isBlank()))) {
            throw GlobalException.of(ApiCode.GOAL_CONTENT_REQUIRED);
        }

        requireUniqueNames(rows);
        requireAllOrNoSubDomains(rows);
    }

    private void requireDatesInSchoolYear(SchoolYearResponse year, List<EduTheme> themes, List<EduBreak> breaks) {
        if (year.startDate() == null || year.endDate() == null) {
            return;
        }
        Stream<LocalDate> dates = Stream.concat(
                themes.stream().flatMap(t -> Stream.of(t.getStartDate(), t.getEndDate())),
                breaks.stream().flatMap(b -> Stream.of(b.getStartDate(), b.getEndDate())));
        boolean outside =
                dates.filter(Objects::nonNull).anyMatch(d -> d.isBefore(year.startDate()) || d.isAfter(year.endDate()));
        if (outside) {
            throw GlobalException.of(ApiCode.THEME_DATE_OUT_OF_YEAR, year.startDate(), year.endDate());
        }
    }

    private void requireThemeWeeks(List<EduTheme> themes) {
        List<EduTheme> dated = themes.stream()
                .filter(t -> t.getStartDate() != null && t.getEndDate() != null)
                .sorted(Comparator.comparing(EduTheme::getStartDate))
                .toList();
        for (EduTheme theme : dated) {
            if (theme.getStartDate().getDayOfWeek() != DayOfWeek.MONDAY
                    || theme.getEndDate().getDayOfWeek() != DayOfWeek.FRIDAY) {
                throw GlobalException.of(ApiCode.THEME_WEEK_ALIGNMENT, theme.getAbbr());
            }
        }
        for (int i = 1; i < dated.size(); i++) {
            EduTheme previous = dated.get(i - 1);
            EduTheme current = dated.get(i);
            if (!current.getStartDate().isAfter(previous.getEndDate())) {
                throw GlobalException.of(ApiCode.THEME_DATES_OVERLAP, current.getAbbr(), previous.getAbbr());
            }
        }
    }

    private void requireAllOrNoSubDomains(List<EduGoalRow> rows) {
        Set<Long> domainsWithSub = rows.stream()
                .filter(row -> row.getKind() == EduGoalRowKind.SUB)
                .map(EduGoalRow::getDomainId)
                .collect(Collectors.toSet());
        rows.stream()
                .filter(row -> row.getKind() != EduGoalRowKind.SUB
                        && row.getSubDomainId() == null
                        && domainsWithSub.contains(row.getDomainId()))
                .findFirst()
                .ifPresent(row -> {
                    throw GlobalException.of(
                            ApiCode.GOAL_SUB_DOMAIN_MIXED, row.getText().trim());
                });
    }

    private void requireUniqueNames(List<EduGoalRow> rows) {
        Set<Long> goalIds = rows.stream()
                .filter(row -> row.getKind() == EduGoalRowKind.GOAL)
                .map(EduGoalRow::getId)
                .collect(Collectors.toSet());
        Map<Long, List<EduGoalContent>> contentsByGoal = goalIds.isEmpty()
                ? Map.of()
                : goalContentRepository.findAllByGoalRowIdInOrderBySortOrderAsc(goalIds).stream()
                        .collect(Collectors.groupingBy(EduGoalContent::getGoalRowId));
        Set<String> names = new HashSet<>();
        for (EduGoalRow row : rows) {
            String scope = row.getKind() == EduGoalRowKind.SUB
                    ? row.getKind() + "|" + row.getDomainId()
                    : row.getKind() + "|" + row.getDomainId() + "|" + row.getSubDomainId();
            if (!names.add(scope + "|" + nameKey(row.getText()))) {
                throw GlobalException.of(
                        ApiCode.GOAL_NAME_DUPLICATE, row.getText().trim());
            }
            Set<String> contents = new HashSet<>();
            for (EduGoalContent content : contentsByGoal.getOrDefault(row.getId(), List.of())) {
                if (!contents.add(nameKey(content.getText()))) {
                    throw GlobalException.of(
                            ApiCode.GOAL_NAME_DUPLICATE, content.getText().trim());
                }
            }
        }
    }

    private List<EduGoalRow> findRows(Long planId) {
        List<EduGoalRow> rows = new ArrayList<>();
        rows.addAll(subDomainRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId));
        rows.addAll(expectedResultRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId));
        rows.addAll(goalRepository.findAllByYearPlanIdOrderBySortOrderAsc(planId));
        rows.sort(Comparator.comparing(EduGoalRow::getDomainId)
                .thenComparing(EduGoalRow::getSortOrder)
                .thenComparing(EduGoalRow::getId));
        return rows;
    }

    private static String nameKey(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
