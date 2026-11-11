package com.carenest.dto.eduplan;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ClassCalendarPreviewRequest(
        Long classId,
        Long yearPlanId,
        Long expectedBranchVersion,
        CalendarDraft draftCalendar,
        List<Long> orderedThemeIds) {
    public record CalendarDraft(List<ThemeSchedule> themeSchedules, List<BranchSchedule> branches) {
        public CalendarDraft {
            branches = branches == null ? List.of() : List.copyOf(branches);
        }
    }

    public record ThemeSchedule(Long themeId, LocalDate startDate, LocalDate endDate) {}

    public record BranchSchedule(
            String key,
            Long themeId,
            Long sourceTemplateId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, String> weekNotes) {}
}
