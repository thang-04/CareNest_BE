package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.entity.eduplan.EduBreak;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.service.eduplan.impl.EduClassDocServiceImpl.TeachingWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EduTeachingWeeksTest {

    // Chủ đề 3 tuần: Thứ 2 07/09/2026 → Thứ 6 25/09/2026
    private static final List<EduTheme> THEMES = List.of(theme(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 25)));

    @Test
    @DisplayName("EDU-41: nghỉ một ngày thì tuần đó vẫn là tuần học, các ngày khác soạn bình thường")
    void singleDayBreakKeepsTheWeek() {
        List<TeachingWeek> weeks = EduClassDocServiceImpl.teachingWeeks(
                THEMES, List.of(breakOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14))));

        assertThat(weeks)
                .extracting(TeachingWeek::start)
                .containsExactly(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21));
    }

    @Test
    @DisplayName("EDU-41: đợt nghỉ phủ cả Thứ 2 → Thứ 6 thì bỏ tuần, tuần sau đánh số tiếp")
    void wholeWeekBreakSkipsTheWeek() {
        List<TeachingWeek> weeks = EduClassDocServiceImpl.teachingWeeks(
                THEMES, List.of(breakOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18))));

        assertThat(weeks)
                .extracting(TeachingWeek::start)
                .containsExactly(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 21));
        assertThat(weeks).extracting(TeachingWeek::no).containsExactly(1, 2);
    }

    @Test
    @DisplayName("EDU-41: đợt nghỉ vắt qua hai tuần (Thứ 5 → Thứ 3) không bỏ tuần nào; nhiều đợt cùng phủ kín thì bỏ")
    void breaksAreCountedPerDay() {
        assertThat(EduClassDocServiceImpl.teachingWeeks(
                        THEMES, List.of(breakOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 15)))))
                .hasSize(3);
        assertThat(EduClassDocServiceImpl.teachingWeeks(
                        THEMES,
                        List.of(
                                breakOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15)),
                                breakOf(LocalDate.of(2026, 9, 16), null),
                                breakOf(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 18)))))
                .hasSize(2);
    }

    private static EduTheme theme(LocalDate start, LocalDate end) {
        EduTheme theme = EduTheme.create(1L);
        theme.update((short) 0, "Trường mầm non", "TMN", start, end, "", "");
        return theme;
    }

    private static EduBreak breakOf(LocalDate start, LocalDate end) {
        EduBreak item = EduBreak.create(1L);
        item.update((short) 0, "Nghỉ", start, end);
        return item;
    }
}
