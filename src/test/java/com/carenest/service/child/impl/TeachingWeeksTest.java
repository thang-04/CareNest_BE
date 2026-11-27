package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeachingWeeksTest {
    private static final LocalDate S1 = LocalDate.of(2026, 9, 7);

    @Test
    @DisplayName("Đếm tuần có ít nhất một ngày học, bỏ tuần nghỉ trọn các ngày")
    void countsWeeksWithAtLeastOneSchoolDay() {
        LocalDate e1 = S1.plusWeeks(18).minusDays(3);
        LocalDate s2 = S1.plusWeeks(20);
        LocalDate e2 = s2.plusWeeks(17).minusDays(3);

        assertThat(TeachingWeeks.count(S1, e1, s2, e2, List.of())).isEqualTo(35);
        // nghỉ trọn một tuần của học kỳ II ⇒ còn 34 tuần
        LocalDate off = s2.plusWeeks(3);
        assertThat(TeachingWeeks.count(S1, e1, s2, e2, List.of(new TeachingWeeks.Break(off, off.plusDays(4)))))
                .isEqualTo(34);
        // nghỉ lễ giữa tuần (còn ngày học) thì tuần vẫn tính
        assertThat(TeachingWeeks.count(
                        S1, e1, s2, e2, List.of(new TeachingWeeks.Break(off.plusDays(1), off.plusDays(2)))))
                .isEqualTo(35);
    }

    @Test
    @DisplayName("Lịch đúng khung năm học thì không cảnh báo")
    void noWarningForCompliantCalendar() {
        assertThat(TeachingWeeks.warnings(
                        LocalDate.of(2026, 9, 7),
                        LocalDate.of(2027, 1, 8),
                        LocalDate.of(2027, 1, 18),
                        LocalDate.of(2027, 5, 21),
                        LocalDate.of(2026, 9, 5),
                        LocalDate.of(2027, 5, 31),
                        List.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("Cảnh báo thiếu tuần từng học kỳ, học kỳ I và năm học kết thúc muộn")
    void warnsWhenCalendarBreaksFramework() {
        assertThat(TeachingWeeks.warnings(
                        LocalDate.of(2026, 10, 5),
                        LocalDate.of(2027, 1, 18),
                        LocalDate.of(2027, 2, 15),
                        LocalDate.of(2027, 6, 4),
                        LocalDate.of(2026, 10, 5),
                        LocalDate.of(2027, 6, 4),
                        List.of()))
                .containsExactly(
                        "học kỳ I 16 tuần (khung 18)",
                        "học kỳ II 16 tuần (khung 17)",
                        "học kỳ I phải kết thúc trước 18/01/2027",
                        "năm học phải kết thúc trước hoặc trong ngày 31/05/2027");
    }

    @Test
    @DisplayName("Thiếu ngày học kỳ thì chưa tính được")
    void unknownWithoutSemesterDates() {
        assertThat(TeachingWeeks.count(S1, null, S1, S1, List.of())).isNull();
    }
}
