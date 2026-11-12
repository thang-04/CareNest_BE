package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.organization.SchoolYearDetailResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Spec 003 — hạn gói học kỳ / hồ sơ chủ đề (data-model "Hạn", quickstart Q1, Q2b; SC-001/002). */
class EduDeadlineTest {
    private static final LocalDate HAN = LocalDate.of(2026, 11, 16);

    private static String state(LocalDate today, boolean approved, LocalDate firstRelease, LocalDate extended) {
        return EduDeadline.of(HAN, extended, today, approved, firstRelease, null)
                .state();
    }

    @Test
    void nearWindowIsSevenDays() {
        assertThat(state(HAN.minusDays(8), false, null, null)).isEqualTo(EduDeadline.NORMAL);
        assertThat(state(HAN.minusDays(7), false, null, null)).isEqualTo(EduDeadline.NEAR);
        assertThat(EduDeadline.of(HAN, null, HAN.minusDays(7), false, null, null)
                        .daysLeft())
                .isEqualTo(7);
        assertThat(state(HAN.minusDays(1), false, null, null)).isEqualTo(EduDeadline.NEAR);
        assertThat(state(HAN, false, null, null)).isEqualTo(EduDeadline.OVERDUE);
    }

    @Test
    void releasedStateFollowsCurrentStatus() {
        assertThat(state(HAN.plusDays(3), true, HAN.minusDays(2), null)).isEqualTo(EduDeadline.ON_TIME);
        // đang Chờ duyệt lúc tới hạn, duyệt sau hạn
        assertThat(state(HAN.plusDays(3), true, HAN.plusDays(1), null)).isEqualTo(EduDeadline.LATE);
        // đã ban hành rồi mở lại sửa phần tương lai sau hạn ⇒ quá hạn, cần mở hạn (analyze F1)
        assertThat(state(HAN.plusDays(3), false, HAN.minusDays(2), null)).isEqualTo(EduDeadline.OVERDUE);
    }

    @Test
    void extensionMovesDeadline() {
        LocalDate moi = HAN.plusDays(5);
        assertThat(state(HAN.plusDays(1), false, null, moi)).isEqualTo(EduDeadline.NEAR);
        assertThat(state(moi, false, null, moi)).isEqualTo(EduDeadline.OVERDUE);
        assertThat(state(moi.plusDays(1), true, HAN.plusDays(2), moi)).isEqualTo(EduDeadline.ON_TIME);
    }

    @Test
    void classOpenedAfterDeadlineIsNotOverdue() {
        assertThat(EduDeadline.of(HAN, null, HAN.plusDays(10), false, null, HAN.plusDays(3))
                        .state())
                .isEqualTo(EduDeadline.NORMAL);
        assertThat(EduDeadline.of(null, null, HAN, false, null, null).state()).isEqualTo(EduDeadline.NORMAL);
    }

    @Test
    void thirtyFiveWeekYearGivesSemesterOfThemeStart() {
        // Năm 35 tuần: HK1 18 tuần từ 07/09/2026, HK2 17 tuần sau 2 tuần nghỉ Tết
        LocalDate s1 = LocalDate.of(2026, 9, 7);
        LocalDate e1 = s1.plusWeeks(18).minusDays(3);
        LocalDate s2 = s1.plusWeeks(20);
        LocalDate e2 = s2.plusWeeks(17).minusDays(3);
        var year = new SchoolYearDetailResponse(
                1L, "2026-2027", s1, e2, s1, e1, s2, e2, null, 0, null, null, null, null, null, null);
        assertThat(EduSemesters.of(year, s1.plusWeeks(17))).contains(1);
        assertThat(EduSemesters.of(year, e1.plusDays(3))).contains(2); // bắt đầu trong kỳ nghỉ ⇒ kỳ sau
        assertThat(EduSemesters.of(year, s2.plusWeeks(16))).contains(2);
        assertThat(EduSemesters.start(year, 2)).isEqualTo(s2);
        assertThat(EduDeadline.of(EduSemesters.start(year, 2), null, s2.minusDays(7), false, null, null)
                        .state())
                .isEqualTo(EduDeadline.NEAR);
        var unconfigured = new SchoolYearDetailResponse(
                1L, "x", s1, e2, null, null, null, null, null, 0, null, null, null, null, null, null);
        assertThat(EduSemesters.of(unconfigured, s1)).isEmpty();
    }
}
