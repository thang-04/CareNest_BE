package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GraduationAgeTest {
    private static final LocalDate YEAR_START = LocalDate.of(2026, 9, 5);

    @Test
    @DisplayName("Đủ 6 tuổi tính theo năm: sinh cuối năm 2020 được ra trường dù chưa đủ 72 tháng")
    void countsAgeByCalendarYear() {
        assertThat(GraduationAge.oldEnoughForGradeOne(LocalDate.of(2020, 12, 31), YEAR_START))
                .isTrue();
        assertThat(GraduationAge.oldEnoughForGradeOne(LocalDate.of(2020, 1, 1), YEAR_START))
                .isTrue();
        assertThat(GraduationAge.oldEnoughForGradeOne(LocalDate.of(2019, 6, 1), YEAR_START))
                .isTrue();
    }

    @Test
    @DisplayName("Sinh năm 2021 chưa đến tuổi vào lớp 1 của năm học 2026-2027")
    void rejectsYoungerBirthYear() {
        assertThat(GraduationAge.oldEnoughForGradeOne(LocalDate.of(2021, 1, 1), YEAR_START))
                .isFalse();
    }
}
