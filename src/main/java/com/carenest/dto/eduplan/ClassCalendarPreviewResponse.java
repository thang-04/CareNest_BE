package com.carenest.dto.eduplan;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ClassCalendarPreviewResponse(
        CalendarDraft calendar, Counts countsBefore, Counts countsAfter, Long calendarRevision) {
    public record CalendarDraft(
            List<ThemeSchedule> themeSchedules,
            List<BranchSchedule> branches,
            Map<String, String> names,
            Map<String, String> notes) {}

    public record ThemeSchedule(Long themeId, LocalDate startDate, LocalDate endDate) {}

    public record BranchSchedule(
            String key,
            Long themeId,
            Long sourceTemplateId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, String> weekNotes) {}

    public record Counts(Map<String, Integer> themes, Map<String, Integer> branches) {}
}
