package com.carenest.service.eduplan.impl;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

final class EduPlanReviewDiff {
    record Issue(String kind, Long itemId, String reason) {}
    private EduPlanReviewDiff() {}
    static List<Issue> review(YearPlanVersionDiff.Snapshot before, YearPlanVersionDiff.Snapshot after,
            YearPlanVersionDiff.Snapshot team, LocalDate today) {
        Map<Long, YearPlanVersionDiff.ContentItem> old = by(before.contents(), YearPlanVersionDiff.ContentItem::lineage);
        Map<Long, YearPlanVersionDiff.ContentItem> next = by(after.contents(), YearPlanVersionDiff.ContentItem::lineage);
        Map<Long, YearPlanVersionDiff.GoalItem> oldGoals = by(before.goals(), YearPlanVersionDiff.GoalItem::lineage);
        Map<Long, YearPlanVersionDiff.GoalItem> newGoals = by(after.goals(), YearPlanVersionDiff.GoalItem::lineage);
        Map<Long, YearPlanVersionDiff.ThemeItem> themes = by(team.themes(), YearPlanVersionDiff.ThemeItem::lineage);
        List<Issue> result = new ArrayList<>();
        for (var content : team.contents()) {
            var prior = old.get(content.lineage());
            if (prior == null || !ongoing(content, themes, today)) continue;
            var current = next.get(content.lineage());
            String reason = null;
            if (current == null) reason = "SOURCE_REMOVED";
            else if (!Objects.equals(prior.goalLineage(), current.goalLineage())) reason = "REF_LOST";
            else if (!Objects.equals(prior.text(), current.text())
                    || !Objects.equals(oldGoals.get(prior.goalLineage()), newGoals.get(prior.goalLineage()))
                        && !sameGoal(oldGoals.get(prior.goalLineage()), newGoals.get(prior.goalLineage()))) {
                reason = "SOURCE_CHANGED";
            }
            if (reason != null) result.add(new Issue("CONTENT", content.id(), reason));
        }
        var oldThemes = by(before.themes(), YearPlanVersionDiff.ThemeItem::lineage);
        var newThemes = by(after.themes(), YearPlanVersionDiff.ThemeItem::lineage);
        for (var theme : team.themes()) {
            var prior = oldThemes.get(theme.lineage());
            if (prior == null || theme.endDate() != null && theme.endDate().isBefore(today)) continue;
            var current = newThemes.get(theme.lineage());
            if (current == null) result.add(new Issue("THEME", theme.id(), "SOURCE_REMOVED"));
            else if (!sameTheme(prior, current)) result.add(new Issue("THEME", theme.id(), "SOURCE_CHANGED"));
        }
        return List.copyOf(result);
    }
    private static boolean ongoing(YearPlanVersionDiff.ContentItem c,
            Map<Long, YearPlanVersionDiff.ThemeItem> themes, LocalDate today) {
        return c.themeLineages().isEmpty() || c.themeLineages().stream().anyMatch(id -> {
            var t = themes.get(id); return t == null || t.endDate() == null || !t.endDate().isBefore(today);
        });
    }
    private static boolean sameGoal(YearPlanVersionDiff.GoalItem a, YearPlanVersionDiff.GoalItem b) {
        return a != null && b != null && Objects.equals(a.text(), b.text());
    }
    private static boolean sameTheme(YearPlanVersionDiff.ThemeItem a, YearPlanVersionDiff.ThemeItem b) {
        return Objects.equals(a.name(), b.name()) && Objects.equals(a.abbr(), b.abbr())
            && Objects.equals(a.startDate(), b.startDate()) && Objects.equals(a.endDate(), b.endDate());
    }
    private static <T> Map<Long,T> by(List<T> rows, Function<T,Long> key) {
        return rows.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
