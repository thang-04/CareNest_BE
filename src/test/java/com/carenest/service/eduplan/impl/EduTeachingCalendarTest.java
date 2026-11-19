package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.*;

import com.carenest.dto.eduplan.ClassCalendarPreviewRequest;
import com.carenest.exception.GlobalException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EduTeachingCalendarTest {
    private static LocalDate day(int month, int day) {
        return LocalDate.of(2026, month, day);
    }

    private static LocalDate day(int day) {
        return day(9, day);
    }

    @Test
    void unionOfBreaksSkipsOnlyWholeTeachingWeeks() {
        var breaks = List.of(
                new EduTeachingCalendar.BreakRange(day(14), day(16)),
                new EduTeachingCalendar.BreakRange(day(17), day(18)));
        assertThat(EduTeachingCalendar.teachingMondays(day(7), day(25), breaks)).containsExactly(day(7), day(21));
    }

    @Test
    void partialBreakStillCountsAsOneTeachingWeek() {
        assertThat(EduTeachingCalendar.teachingMondays(
                        day(7), day(18), List.of(new EduTeachingCalendar.BreakRange(day(14), day(16)))))
                .containsExactly(day(7), day(14));
    }

    @Test
    void inclusiveDurationBoundsRejectOutsideValues() {
        assertThatCode(() -> EduTeachingCalendar.validate(day(7), day(18), List.of(), 1, 2))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> EduTeachingCalendar.validate(day(7), day(25), List.of(), 1, 2))
                .isInstanceOf(GlobalException.class);
    }

    @Test
    void datesMustBeMondayThroughFriday() {
        assertThatThrownBy(() -> EduTeachingCalendar.validate(day(8), day(18), List.of(), 1, 2))
                .isInstanceOf(GlobalException.class);
    }

    @Test
    @DisplayName("T039: reorderThemeBlocks permutation validation - duplicate, missing, foreign reject")
    void reorderThemeBlocksPermutationValidation() {
        var draft = new ClassCalendarPreviewRequest.CalendarDraft(
                List.of(
                        new ClassCalendarPreviewRequest.ThemeSchedule(1L, day(7), day(18)),
                        new ClassCalendarPreviewRequest.ThemeSchedule(2L, day(21), day(10, 2))),
                List.of());
        // Duplicate
        assertThatThrownBy(() -> EduTeachingCalendar.reorderThemeBlocks(draft, List.of(1L, 1L), List.of(), null))
                .isInstanceOf(GlobalException.class);
        // Missing
        assertThatThrownBy(() -> EduTeachingCalendar.reorderThemeBlocks(draft, List.of(1L), List.of(), null))
                .isInstanceOf(GlobalException.class);
        // Foreign
        assertThatThrownBy(() -> EduTeachingCalendar.reorderThemeBlocks(draft, List.of(1L, 99L), List.of(), null))
                .isInstanceOf(GlobalException.class);
    }

    @Test
    @DisplayName("T039: A2/B4 reorder to B4/A2 preserves counts, offsets, and weekNotes")
    void reorderPreservesThemeAndBranchWeekCountsAndNotes() {
        // Theme 1: 2 weeks (Sep 7 - Sep 18)
        // Theme 2: 4 weeks (Sep 21 - Oct 16)
        var draft = new ClassCalendarPreviewRequest.CalendarDraft(
                List.of(
                        new ClassCalendarPreviewRequest.ThemeSchedule(1L, day(7), day(18)),
                        new ClassCalendarPreviewRequest.ThemeSchedule(2L, day(21), day(10, 16))),
                List.of(
                        new ClassCalendarPreviewRequest.BranchSchedule(
                                "b1", 1L, null, "Branch 1", day(7), day(18), Map.of("0", "Note 1A", "1", "Note 1B")),
                        new ClassCalendarPreviewRequest.BranchSchedule(
                                "b2", 2L, null, "Branch 2", day(21), day(10, 2), Map.of("0", "Note 2A")),
                        new ClassCalendarPreviewRequest.BranchSchedule(
                                "b3", 2L, null, "Branch 3", day(10, 5), day(10, 16), Map.of("0", "Note 3A"))));

        var limits = new EduTeachingCalendar.DurationLimits(2, 4, 1, 2);
        var result = EduTeachingCalendar.reorderThemeBlocks(draft, List.of(2L, 1L), List.of(), limits);

        // Theme 2 is now first (4 weeks: Sep 7 to Oct 2)
        var theme2 = result.calendar().themeSchedules().stream()
                .filter(t -> t.themeId().equals(2L))
                .findFirst()
                .orElseThrow();
        assertThat(theme2.startDate()).isEqualTo(day(7));
        assertThat(theme2.endDate()).isEqualTo(day(10, 2));

        // Theme 1 is now second (2 weeks: Oct 5 to Oct 16)
        var theme1 = result.calendar().themeSchedules().stream()
                .filter(t -> t.themeId().equals(1L))
                .findFirst()
                .orElseThrow();
        assertThat(theme1.startDate()).isEqualTo(day(10, 5));
        assertThat(theme1.endDate()).isEqualTo(day(10, 16));

        // Branch 1 (2 weeks) now spans Oct 5 to Oct 16
        var branch1 = result.calendar().branches().stream()
                .filter(b -> b.key().equals("b1"))
                .findFirst()
                .orElseThrow();
        assertThat(branch1.startDate()).isEqualTo(day(10, 5));
        assertThat(branch1.endDate()).isEqualTo(day(10, 16));
        assertThat(branch1.weekNotes()).containsEntry("0", "Note 1A").containsEntry("1", "Note 1B");

        // Projections
        assertThat(result.calendar().names()).containsEntry(day(10, 5).toString(), "Branch 1");
        assertThat(result.calendar().names()).containsEntry(day(10, 12).toString(), "Branch 1");
        assertThat(result.calendar().notes()).containsEntry(day(10, 5).toString(), "Note 1A");
        assertThat(result.calendar().notes()).containsEntry(day(10, 12).toString(), "Note 1B");

        // Counts before and after
        assertThat(result.countsBefore().themes()).containsEntry("1", 2).containsEntry("2", 4);
        assertThat(result.countsAfter().themes()).containsEntry("1", 2).containsEntry("2", 4);
        assertThat(result.countsBefore().branches())
                .containsEntry("b1", 2)
                .containsEntry("b2", 2)
                .containsEntry("b3", 2);
        assertThat(result.countsAfter().branches())
                .containsEntry("b1", 2)
                .containsEntry("b2", 2)
                .containsEntry("b3", 2);
    }

    @Test
    void rejectsOverlappingBranchesAndNotesOutsideTheirRange() {
        var themes = List.of(new ClassCalendarPreviewRequest.ThemeSchedule(1L, day(7), day(18)));
        var first = new ClassCalendarPreviewRequest.BranchSchedule("a", 1L, null, "A", day(7), day(11), Map.of());
        var overlap = new ClassCalendarPreviewRequest.BranchSchedule("b", 1L, null, "B", day(7), day(11), Map.of());
        assertThatThrownBy(() -> EduTeachingCalendar.reorderThemeBlocks(
                        new ClassCalendarPreviewRequest.CalendarDraft(themes, List.of(first, overlap)),
                        List.of(1L),
                        List.of(),
                        null))
                .isInstanceOf(GlobalException.class);
        var notes = new ClassCalendarPreviewRequest.BranchSchedule(
                "a", 1L, null, "A", day(7), day(11), Map.of("1", "Ngoài nhánh"));
        assertThatThrownBy(() -> EduTeachingCalendar.reorderThemeBlocks(
                        new ClassCalendarPreviewRequest.CalendarDraft(themes, List.of(notes)),
                        List.of(1L),
                        List.of(),
                        null))
                .isInstanceOf(GlobalException.class);
    }
}
