package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.ClassCalendarPreviewRequest;
import com.carenest.dto.eduplan.ClassCalendarPreviewResponse;
import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class EduTeachingCalendar {
    record BreakRange(LocalDate start, LocalDate end) {}

    record DurationLimits(int themeMin, int themeMax, int branchMin, int branchMax) {}

    record ReorderResult(
            ClassCalendarPreviewResponse.CalendarDraft calendar,
            ClassCalendarPreviewResponse.Counts countsBefore,
            ClassCalendarPreviewResponse.Counts countsAfter) {}

    static List<LocalDate> teachingMondays(LocalDate start, LocalDate end, List<BreakRange> breaks) {
        requireWholeWeeks(start, end);
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate monday = start; !monday.isAfter(end); monday = monday.plusWeeks(1)) {
            boolean wholeBreak = true;
            for (int offset = 0; offset < 5; offset++) {
                LocalDate day = monday.plusDays(offset);
                boolean off = breaks.stream()
                        .anyMatch(b -> b.start() != null
                                && b.end() != null
                                && !day.isBefore(b.start())
                                && !day.isAfter(b.end()));
                if (!off) {
                    wholeBreak = false;
                    break;
                }
            }
            if (!wholeBreak) result.add(monday);
        }
        return List.copyOf(result);
    }

    static void validate(LocalDate start, LocalDate end, List<BreakRange> breaks, int min, int max) {
        int count = teachingMondays(start, end, breaks).size();
        if (count < min || count > max)
            throw GlobalException.of(
                    ApiCode.EDUCATION_CALENDAR_INVALID, "thời lượng phải từ " + min + " đến " + max + " tuần học");
    }

    static ReorderResult reorderThemeBlocks(
            ClassCalendarPreviewRequest.CalendarDraft base,
            List<Long> orderedThemeIds,
            List<BreakRange> breaks,
            DurationLimits limits) {
        if (base == null
                || base.themeSchedules() == null
                || base.themeSchedules().isEmpty()) {
            throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "lịch trống");
        }
        if (orderedThemeIds == null || orderedThemeIds.isEmpty()) {
            throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "thứ tự chủ đề không hợp lệ");
        }
        Set<Long> baseThemeIds = base.themeSchedules().stream()
                .map(ClassCalendarPreviewRequest.ThemeSchedule::themeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> orderedSet = new LinkedHashSet<>(orderedThemeIds);
        if (orderedThemeIds.size() != orderedSet.size() || !baseThemeIds.equals(orderedSet)) {
            throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "thứ tự chủ đề phải là hoán vị hợp lệ");
        }

        Map<Long, Integer> themeWeeksMap = new LinkedHashMap<>();
        Map<Long, List<LocalDate>> themeMondaysMap = new LinkedHashMap<>();
        for (ClassCalendarPreviewRequest.ThemeSchedule theme : base.themeSchedules()) {
            List<LocalDate> mondays = teachingMondays(theme.startDate(), theme.endDate(), breaks);
            if (mondays.isEmpty()) {
                throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "chủ đề chưa có lịch tuần học");
            }
            if (limits != null && (mondays.size() < limits.themeMin() || mondays.size() > limits.themeMax())) {
                throw GlobalException.of(
                        ApiCode.EDUCATION_CALENDAR_INVALID, "thời lượng chủ đề ngoài giới hạn cấu hình");
            }
            themeWeeksMap.put(theme.themeId(), mondays.size());
            themeMondaysMap.put(theme.themeId(), mondays);
        }

        Map<String, Integer> branchWeeksMap = new LinkedHashMap<>();
        Map<String, Integer> branchOffsetMap = new LinkedHashMap<>();
        List<ClassCalendarPreviewRequest.BranchSchedule> branches =
                base.branches() == null ? List.of() : base.branches();
        Set<LocalDate> occupied = new LinkedHashSet<>();
        for (ClassCalendarPreviewRequest.BranchSchedule branch : branches) {
            List<LocalDate> branchMondays = teachingMondays(branch.startDate(), branch.endDate(), breaks);
            if (branchMondays.isEmpty()) {
                throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "nhánh chưa có lịch tuần học");
            }
            if (limits != null
                    && (branchMondays.size() < limits.branchMin() || branchMondays.size() > limits.branchMax())) {
                throw GlobalException.of(
                        ApiCode.EDUCATION_CALENDAR_INVALID, "thời lượng nhánh ngoài giới hạn cấu hình");
            }
            List<LocalDate> parentMondays = themeMondaysMap.get(branch.themeId());
            if (parentMondays == null) {
                throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "nhánh không thuộc chủ đề trong lịch");
            }
            int offset = parentMondays.indexOf(branchMondays.getFirst());
            if (offset < 0
                    || offset + branchMondays.size() > parentMondays.size()
                    || !parentMondays.containsAll(branchMondays)) {
                throw GlobalException.of(
                        ApiCode.EDUCATION_CALENDAR_INVALID, "nhánh nằm ngoài khoảng thời gian của chủ đề cha");
            }
            if (branchMondays.stream().anyMatch(day -> !occupied.add(day)))
                throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "các nhánh không được trùng tuần");
            if (branch.weekNotes() != null)
                for (String key : branch.weekNotes().keySet()) {
                    try {
                        int note = Integer.parseInt(key);
                        if (note < 0 || note >= branchMondays.size()) throw new IllegalArgumentException();
                    } catch (IllegalArgumentException ex) {
                        throw GlobalException.of(
                                ApiCode.EDUCATION_CALENDAR_INVALID, "ghi chú không thuộc tuần của nhánh");
                    }
                }
            branchWeeksMap.put(branch.key(), branchMondays.size());
            branchOffsetMap.put(branch.key(), offset);
        }

        List<LocalDate> axis = themeMondaysMap.values().stream()
                .flatMap(List::stream)
                .sorted()
                .distinct()
                .toList();
        int totalExpectedWeeks =
                themeWeeksMap.values().stream().mapToInt(Integer::intValue).sum();
        if (axis.size() != totalExpectedWeeks) {
            throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "các chủ đề trong lịch không được trùng ngày");
        }

        int axisIndex = 0;
        List<ClassCalendarPreviewResponse.ThemeSchedule> newThemes = new ArrayList<>();
        Map<Long, List<LocalDate>> newThemeMondaysMap = new LinkedHashMap<>();
        Map<String, String> namesProjection = new LinkedHashMap<>();
        Map<String, String> notesProjection = new LinkedHashMap<>();

        for (Long themeId : orderedThemeIds) {
            int weeks = themeWeeksMap.get(themeId);
            List<LocalDate> newMondays = axis.subList(axisIndex, axisIndex + weeks);
            axisIndex += weeks;
            newThemeMondaysMap.put(themeId, newMondays);
            LocalDate newStart = newMondays.getFirst();
            LocalDate newEnd = newMondays.getLast().plusDays(4);

            List<LocalDate> actualRangeMondays = teachingMondays(newStart, newEnd, breaks);
            if (!actualRangeMondays.equals(newMondays)) {
                throw GlobalException.of(
                        ApiCode.EDUCATION_CALENDAR_INVALID,
                        "khoảng trống giữa các tuần học không thể biểu diễn khi đổi thứ tự");
            }
            newThemes.add(new ClassCalendarPreviewResponse.ThemeSchedule(themeId, newStart, newEnd));
        }

        List<ClassCalendarPreviewResponse.BranchSchedule> newBranches = new ArrayList<>();
        for (ClassCalendarPreviewRequest.BranchSchedule branch : branches) {
            List<LocalDate> parentNewMondays = newThemeMondaysMap.get(branch.themeId());
            int offset = branchOffsetMap.get(branch.key());
            int weeks = branchWeeksMap.get(branch.key());
            List<LocalDate> branchNewMondays = parentNewMondays.subList(offset, offset + weeks);
            LocalDate bStart = branchNewMondays.getFirst();
            LocalDate bEnd = branchNewMondays.getLast().plusDays(4);

            for (int i = 0; i < branchNewMondays.size(); i++) {
                LocalDate m = branchNewMondays.get(i);
                String mKey = m.toString();
                if (branch.name() != null && !branch.name().isBlank()) {
                    namesProjection.put(mKey, branch.name());
                }
                if (branch.weekNotes() != null) {
                    String note = branch.weekNotes().get(String.valueOf(i));
                    if (note != null && !note.isBlank()) {
                        notesProjection.put(mKey, note);
                    }
                }
            }

            newBranches.add(new ClassCalendarPreviewResponse.BranchSchedule(
                    branch.key(),
                    branch.themeId(),
                    branch.sourceTemplateId(),
                    branch.name(),
                    bStart,
                    bEnd,
                    branch.weekNotes() == null ? Map.of() : branch.weekNotes()));
        }

        Map<String, Integer> themeCounts = new LinkedHashMap<>();
        themeWeeksMap.forEach((k, v) -> themeCounts.put(String.valueOf(k), v));
        Map<String, Integer> branchCounts = new LinkedHashMap<>(branchWeeksMap);
        ClassCalendarPreviewResponse.Counts countsBefore =
                new ClassCalendarPreviewResponse.Counts(themeCounts, branchCounts);
        ClassCalendarPreviewResponse.Counts countsAfter =
                new ClassCalendarPreviewResponse.Counts(themeCounts, branchCounts);

        ClassCalendarPreviewResponse.CalendarDraft draftResponse = new ClassCalendarPreviewResponse.CalendarDraft(
                List.copyOf(newThemes),
                List.copyOf(newBranches),
                Map.copyOf(namesProjection),
                Map.copyOf(notesProjection));

        return new ReorderResult(draftResponse, countsBefore, countsAfter);
    }

    private static void requireWholeWeeks(LocalDate start, LocalDate end) {
        if (start == null
                || end == null
                || end.isBefore(start)
                || start.getDayOfWeek() != DayOfWeek.MONDAY
                || end.getDayOfWeek() != DayOfWeek.FRIDAY) {
            throw GlobalException.of(ApiCode.EDUCATION_CALENDAR_INVALID, "bắt đầu Thứ Hai và kết thúc Thứ Sáu");
        }
    }
}
