package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EduPlanReviewDiffTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static YearPlanVersionDiff.Snapshot source(String text, Long goalLineage, short number) {
        return new YearPlanVersionDiff.Snapshot(1L, 1,
                List.of(new YearPlanVersionDiff.GoalItem(10L, goalLineage, number, "Goal")),
                List.of(new YearPlanVersionDiff.ContentItem(20L, 20L, goalLineage, text, Set.of())), List.of());
    }
    private static YearPlanVersionDiff.Snapshot team(LocalDate end) {
        return new YearPlanVersionDiff.Snapshot(2L, 1,
                List.of(new YearPlanVersionDiff.GoalItem(110L, 10L, (short) 1, "Goal")),
                List.of(new YearPlanVersionDiff.ContentItem(120L, 20L, 10L, "old", Set.of(30L))),
                List.of(new YearPlanVersionDiff.ThemeItem(130L, 30L, "Theme", "T", TODAY.minusDays(7), end)));
    }
    @Test void changedContentUsesTeamIdAndSkipsOnlyEndedMappings() {
        var before = source("old", 10L, (short) 1);
        var after = source("new", 10L, (short) 1);
        assertThat(EduPlanReviewDiff.review(before, after, team(TODAY), TODAY))
                .contains(new EduPlanReviewDiff.Issue("CONTENT", 120L, "SOURCE_CHANGED"));
        assertThat(EduPlanReviewDiff.review(before, after, team(TODAY.minusDays(1)), TODAY)).isEmpty();
    }
    @Test void renumberAndNewContentDoNotCreateMandatoryReview() {
        assertThat(EduPlanReviewDiff.review(source("old", 10L, (short) 1),
                source("old", 10L, (short) 8), team(TODAY), TODAY)).isEmpty();
        var empty = new YearPlanVersionDiff.Snapshot(1L, 1, List.of(), List.of(), List.of());
        assertThat(EduPlanReviewDiff.review(empty, source("new", 10L, (short) 1), team(TODAY), TODAY)).isEmpty();
    }
    @Test void removedSourceAndChangedParentAreDetected() {
        var empty = new YearPlanVersionDiff.Snapshot(1L, 2, List.of(), List.of(), List.of());
        assertThat(EduPlanReviewDiff.review(source("old", 10L, (short) 1), empty, team(TODAY), TODAY))
                .contains(new EduPlanReviewDiff.Issue("CONTENT", 120L, "SOURCE_REMOVED"));
        assertThat(EduPlanReviewDiff.review(source("old", 10L, (short) 1),
                source("old", 11L, (short) 1), team(TODAY), TODAY))
                .contains(new EduPlanReviewDiff.Issue("CONTENT", 120L, "REF_LOST"));
    }
}
