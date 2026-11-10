package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.eduplan.YearPlanChangesResponse;
import com.carenest.dto.eduplan.YearPlanChangesResponse.Change;
import com.carenest.service.eduplan.impl.YearPlanVersionDiff.ContentItem;
import com.carenest.service.eduplan.impl.YearPlanVersionDiff.GoalItem;
import com.carenest.service.eduplan.impl.YearPlanVersionDiff.Snapshot;
import com.carenest.service.eduplan.impl.YearPlanVersionDiff.ThemeItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class YearPlanVersionDiffTest {

    private static final LocalDate SEP_7 = LocalDate.of(2026, 9, 7);
    private static final LocalDate SEP_25 = LocalDate.of(2026, 9, 25);

    // v1: MT1 (lineage 1), MT2 (lineage 2), MT3 (lineage 3); chủ đề 10, 11, 12
    private static final Snapshot V1 = new Snapshot(
            100L,
            1,
            List.of(
                    new GoalItem(1L, 1L, (short) 1, "Trẻ chạy"),
                    new GoalItem(2L, 2L, (short) 2, "Trẻ nhảy"),
                    new GoalItem(3L, 3L, (short) 3, "Trẻ hát")),
            List.of(
                    new ContentItem(21L, 21L, 1L, "Chạy chậm", Set.of(10L)),
                    new ContentItem(22L, 22L, 2L, "Bật xa", Set.of(10L)),
                    new ContentItem(23L, 23L, 2L, "Bật cao", Set.of(11L)),
                    new ContentItem(24L, 24L, 3L, "Hát", Set.of(11L))),
            List.of(
                    new ThemeItem(10L, 10L, "Trường", "TMN", SEP_7, SEP_25),
                    new ThemeItem(11L, 11L, "Gia đình", "GĐ", SEP_7, SEP_25),
                    new ThemeItem(12L, 12L, "Tết", "T", SEP_7, SEP_25)));

    @Test
    @DisplayName("Chèn MT làm đánh số lại không tính là đổi; đổi chữ, bỏ, thêm, đổi tích, đổi chủ đề phân loại đúng")
    void classifiesByLineage() {
        Snapshot v2 = new Snapshot(
                200L,
                2,
                List.of(
                        new GoalItem(31L, 31L, (short) 1, "Trẻ đi"),
                        new GoalItem(32L, 1L, (short) 2, "Trẻ chạy "),
                        new GoalItem(33L, 2L, (short) 3, "Trẻ nhảy xa")),
                List.of(
                        new ContentItem(41L, 21L, 1L, "Chạy chậm", Set.of(10L)),
                        new ContentItem(42L, 22L, 2L, "Bật xa", Set.of(110L)),
                        new ContentItem(43L, 43L, 31L, "Đi thăng bằng", Set.of())),
                List.of(
                        new ThemeItem(50L, 10L, "Trường", "TMN", SEP_7, SEP_25),
                        new ThemeItem(51L, 11L, "Gia đình bé", "GĐ", SEP_7, SEP_25.plusDays(7)),
                        new ThemeItem(110L, 110L, "Mới", "M", SEP_7, SEP_25)));

        YearPlanChangesResponse changes = YearPlanVersionDiff.compare(V1, v2);

        Map<Long, YearPlanChangesResponse.Goal> goals =
                byLineage(changes.goals(), YearPlanChangesResponse.Goal::lineageId);
        assertThat(goals.get(31L).change()).isEqualTo(Change.ADDED);
        // MT1 → MT2 vì chèn dòng, chữ chỉ khác khoảng trắng: không đổi
        assertThat(goals.get(1L).change()).isEqualTo(Change.UNCHANGED);
        assertThat(goals.get(1L).goalNo()).isEqualTo((short) 2);
        assertThat(goals.get(1L).fromGoalNo()).isEqualTo((short) 1);
        assertThat(goals.get(2L).change()).isEqualTo(Change.CHANGED);
        assertThat(goals.get(2L).fields()).containsExactly("text", "contents");
        assertThat(goals.get(3L).change()).isEqualTo(Change.REMOVED);
        assertThat(goals.get(3L).fromId()).isEqualTo(3L);

        Map<Long, YearPlanChangesResponse.Content> contents =
                byLineage(changes.contents(), YearPlanChangesResponse.Content::lineageId);
        assertThat(contents.get(21L).change()).isEqualTo(Change.UNCHANGED);
        assertThat(contents.get(22L).change()).isEqualTo(Change.CHANGED);
        assertThat(contents.get(22L).fields()).containsExactly("themes");
        assertThat(contents.get(23L).change()).isEqualTo(Change.REMOVED);
        assertThat(contents.get(43L).change()).isEqualTo(Change.ADDED);

        Map<Long, YearPlanChangesResponse.Theme> themes =
                byLineage(changes.themes(), YearPlanChangesResponse.Theme::lineageId);
        assertThat(themes.get(10L).change()).isEqualTo(Change.UNCHANGED);
        assertThat(themes.get(10L).id()).isEqualTo(50L);
        assertThat(themes.get(11L).fields()).containsExactly("name", "dates");
        assertThat(themes.get(12L).change()).isEqualTo(Change.REMOVED);
        assertThat(themes.get(110L).change()).isEqualTo(Change.ADDED);
    }

    @Test
    @DisplayName("Hai phiên bản giống nhau: không có mục đổi")
    void identicalVersionsHaveNoChanges() {
        YearPlanChangesResponse changes = YearPlanVersionDiff.compare(V1, V1);
        assertThat(changes.goals()).allMatch(g -> g.change() == Change.UNCHANGED);
        assertThat(changes.contents()).allMatch(c -> c.change() == Change.UNCHANGED);
        assertThat(changes.themes()).allMatch(t -> t.change() == Change.UNCHANGED);
    }

    private static <T> Map<Long, T> byLineage(List<T> items, Function<T, Long> lineage) {
        return items.stream().collect(Collectors.toMap(lineage, Function.identity()));
    }
}
