package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.YearPlanChangesResponse;
import com.carenest.dto.eduplan.YearPlanChangesResponse.Change;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * So sánh hai phiên bản kế hoạch năm theo lineage (spec 004, research R6). Đổi số MT, thứ tự, lĩnh vực không tính là
 * đổi; MT đổi khi câu MT đổi hoặc nội dung của nó đổi chữ / bị bỏ / thêm / đổi chủ đề tích.
 */
final class YearPlanVersionDiff {

    static final String FIELD_TEXT = "text";
    static final String FIELD_CONTENTS = "contents";
    static final String FIELD_THEMES = "themes";
    static final String FIELD_NAME = "name";
    static final String FIELD_ABBR = "abbr";
    static final String FIELD_DATES = "dates";

    record GoalItem(Long id, Long lineage, Short goalNo, String text) {}

    /** themeLineages = lineage các chủ đề được tích, để so dấu tích giữa hai phiên bản. */
    record ContentItem(Long id, Long lineage, Long goalLineage, String text, Set<Long> themeLineages) {}

    record ThemeItem(Long id, Long lineage, String name, String abbr, LocalDate startDate, LocalDate endDate) {}

    record Snapshot(
            Long planId, int versionNo, List<GoalItem> goals, List<ContentItem> contents, List<ThemeItem> themes) {}

    private YearPlanVersionDiff() {}

    static YearPlanChangesResponse compare(Snapshot from, Snapshot to) {
        List<YearPlanChangesResponse.Content> contents = compareContents(from.contents(), to.contents());
        Set<Long> goalsWithContentChange = contents.stream()
                .filter(c -> c.change() != Change.UNCHANGED)
                .map(YearPlanChangesResponse.Content::goalLineageId)
                .collect(Collectors.toSet());
        return new YearPlanChangesResponse(
                to.planId(),
                to.versionNo(),
                from.planId(),
                from.versionNo(),
                compareGoals(from.goals(), to.goals(), goalsWithContentChange),
                contents,
                compareThemes(from.themes(), to.themes()));
    }

    private static List<YearPlanChangesResponse.Goal> compareGoals(
            List<GoalItem> from, List<GoalItem> to, Set<Long> goalsWithContentChange) {
        Map<Long, GoalItem> old = byLineage(from, GoalItem::lineage);
        Set<Long> seen = new HashSet<>();
        List<YearPlanChangesResponse.Goal> out = new ArrayList<>();
        for (GoalItem goal : to) {
            GoalItem before = old.get(goal.lineage());
            seen.add(goal.lineage());
            if (before == null) {
                out.add(new YearPlanChangesResponse.Goal(
                        goal.lineage(),
                        goal.id(),
                        null,
                        Change.ADDED,
                        List.of(),
                        goal.goalNo(),
                        null,
                        goal.text(),
                        null));
                continue;
            }
            List<String> fields = new ArrayList<>();
            if (!same(before.text(), goal.text())) {
                fields.add(FIELD_TEXT);
            }
            if (goalsWithContentChange.contains(goal.lineage())) {
                fields.add(FIELD_CONTENTS);
            }
            out.add(new YearPlanChangesResponse.Goal(
                    goal.lineage(),
                    goal.id(),
                    before.id(),
                    fields.isEmpty() ? Change.UNCHANGED : Change.CHANGED,
                    fields,
                    goal.goalNo(),
                    before.goalNo(),
                    goal.text(),
                    before.text()));
        }
        for (GoalItem before : from) {
            if (!seen.contains(before.lineage())) {
                out.add(new YearPlanChangesResponse.Goal(
                        before.lineage(),
                        null,
                        before.id(),
                        Change.REMOVED,
                        List.of(),
                        null,
                        before.goalNo(),
                        null,
                        before.text()));
            }
        }
        return out;
    }

    private static List<YearPlanChangesResponse.Content> compareContents(List<ContentItem> from, List<ContentItem> to) {
        Map<Long, ContentItem> old = byLineage(from, ContentItem::lineage);
        Set<Long> seen = new HashSet<>();
        List<YearPlanChangesResponse.Content> out = new ArrayList<>();
        for (ContentItem content : to) {
            ContentItem before = old.get(content.lineage());
            seen.add(content.lineage());
            if (before == null) {
                out.add(new YearPlanChangesResponse.Content(
                        content.lineage(),
                        content.id(),
                        null,
                        content.goalLineage(),
                        Change.ADDED,
                        List.of(),
                        content.text()));
                continue;
            }
            List<String> fields = new ArrayList<>();
            if (!same(before.text(), content.text())) {
                fields.add(FIELD_TEXT);
            }
            if (!before.themeLineages().equals(content.themeLineages())) {
                fields.add(FIELD_THEMES);
            }
            out.add(new YearPlanChangesResponse.Content(
                    content.lineage(),
                    content.id(),
                    before.id(),
                    content.goalLineage(),
                    fields.isEmpty() ? Change.UNCHANGED : Change.CHANGED,
                    fields,
                    content.text()));
        }
        for (ContentItem before : from) {
            if (!seen.contains(before.lineage())) {
                out.add(new YearPlanChangesResponse.Content(
                        before.lineage(),
                        null,
                        before.id(),
                        before.goalLineage(),
                        Change.REMOVED,
                        List.of(),
                        before.text()));
            }
        }
        return out;
    }

    private static List<YearPlanChangesResponse.Theme> compareThemes(List<ThemeItem> from, List<ThemeItem> to) {
        Map<Long, ThemeItem> old = byLineage(from, ThemeItem::lineage);
        Set<Long> seen = new HashSet<>();
        List<YearPlanChangesResponse.Theme> out = new ArrayList<>();
        for (ThemeItem theme : to) {
            ThemeItem before = old.get(theme.lineage());
            seen.add(theme.lineage());
            if (before == null) {
                out.add(new YearPlanChangesResponse.Theme(
                        theme.lineage(), theme.id(), null, Change.ADDED, List.of(), theme.name(), null));
                continue;
            }
            List<String> fields = new ArrayList<>();
            if (!same(before.name(), theme.name())) {
                fields.add(FIELD_NAME);
            }
            if (!same(before.abbr(), theme.abbr())) {
                fields.add(FIELD_ABBR);
            }
            if (!Objects.equals(before.startDate(), theme.startDate())
                    || !Objects.equals(before.endDate(), theme.endDate())) {
                fields.add(FIELD_DATES);
            }
            out.add(new YearPlanChangesResponse.Theme(
                    theme.lineage(),
                    theme.id(),
                    before.id(),
                    fields.isEmpty() ? Change.UNCHANGED : Change.CHANGED,
                    fields,
                    theme.name(),
                    before.name()));
        }
        for (ThemeItem before : from) {
            if (!seen.contains(before.lineage())) {
                out.add(new YearPlanChangesResponse.Theme(
                        before.lineage(), null, before.id(), Change.REMOVED, List.of(), null, before.name()));
            }
        }
        return out;
    }

    // So chữ bỏ khoảng trắng đầu/cuối như lúc lưu (trimToEmpty)
    private static boolean same(String a, String b) {
        return Objects.equals(a == null ? "" : a.trim(), b == null ? "" : b.trim());
    }

    private static <T> Map<Long, T> byLineage(List<T> items, Function<T, Long> lineage) {
        return items.stream()
                .collect(Collectors.toMap(lineage, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    }
}
