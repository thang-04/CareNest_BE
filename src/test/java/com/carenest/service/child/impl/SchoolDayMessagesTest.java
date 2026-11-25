package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.organization.SchoolHolidayDtos.SchoolHolidayItem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchoolDayMessagesTest {

    private static final String LABEL = "Ngày vào lớp";

    @Test
    void saturdayNamesTheDay() {
        assertThat(SchoolDayMessages.problem(LABEL, LocalDate.of(2026, 11, 28), List.of()))
                .isEqualTo("Ngày vào lớp 28/11/2026 là thứ Bảy. Chọn ngày từ thứ 2 tới thứ 6.");
    }

    @Test
    void sundayNamesTheDay() {
        assertThat(SchoolDayMessages.problem(LABEL, LocalDate.of(2026, 11, 29), List.of()))
                .isEqualTo("Ngày vào lớp 29/11/2026 là Chủ nhật. Chọn ngày từ thứ 2 tới thứ 6.");
    }

    @Test
    void singleDayHolidayNamesTheHoliday() {
        List<SchoolHolidayItem> holidays =
                List.of(new SchoolHolidayItem(LocalDate.of(2026, 12, 9), LocalDate.of(2026, 12, 9), "Nghỉ thử"));
        assertThat(SchoolDayMessages.problem(LABEL, LocalDate.of(2026, 12, 9), holidays))
                .isEqualTo(
                        "Ngày vào lớp 09/12/2026 trùng ngày nghỉ của trường: Nghỉ thử (09/12/2026). Chọn ngày học khác.");
    }

    @Test
    void multiDayHolidayShowsRangeWithEnDash() {
        List<SchoolHolidayItem> holidays =
                List.of(new SchoolHolidayItem(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), "Tết Dương lịch"));
        assertThat(SchoolDayMessages.problem(LABEL, LocalDate.of(2027, 1, 1), holidays))
                .isEqualTo("Ngày vào lớp 01/01/2027 trùng ngày nghỉ của trường: Tết Dương lịch"
                        + " (01/01/2027 – 03/01/2027). Chọn ngày học khác.");
    }

    @Test
    void schoolDayReturnsNull() {
        assertThat(SchoolDayMessages.problem(LABEL, LocalDate.of(2026, 11, 25), List.of()))
                .isNull();
    }

    @Test
    void weekdayOutsideAnyHolidayFallsBackToGenericSentence() {
        assertThat(SchoolDayMessages.explain(LABEL, LocalDate.of(2026, 11, 25), List.of()))
                .isEqualTo("Ngày vào lớp 25/11/2026 không phải ngày học. Chọn ngày học khác.");
    }
}
