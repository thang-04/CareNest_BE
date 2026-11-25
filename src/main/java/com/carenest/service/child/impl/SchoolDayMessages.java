package com.carenest.service.child.impl;

import com.carenest.dto.organization.SchoolHolidayDtos.SchoolHolidayItem;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** G94-180/CHD-14: câu lỗi nói rõ vì sao một ngày không phải ngày học (thứ 7, chủ nhật hoặc ngày nghỉ của trường). */
final class SchoolDayMessages {
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SchoolDayMessages() {}

    /** Câu lỗi khi ngày là cuối tuần hoặc nằm trong đợt nghỉ; null khi là ngày học. */
    static String problem(String label, LocalDate date, List<SchoolHolidayItem> holidays) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return label + " " + DMY.format(date) + " là thứ Bảy. Chọn ngày từ thứ 2 tới thứ 6.";
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return label + " " + DMY.format(date) + " là Chủ nhật. Chọn ngày từ thứ 2 tới thứ 6.";
        }
        return holidays.stream()
                .filter(h -> !date.isBefore(h.startDate()) && !date.isAfter(h.endDate()))
                .findFirst()
                .map(h -> label + " " + DMY.format(date) + " trùng ngày nghỉ của trường: " + h.name() + " (" + range(h)
                        + "). Chọn ngày học khác.")
                .orElse(null);
    }

    /** Như {@link #problem} nhưng luôn trả câu: ngày đã biết không phải ngày học mà không khớp lý do nào. */
    static String explain(String label, LocalDate date, List<SchoolHolidayItem> holidays) {
        String problem = problem(label, date, holidays);
        return problem != null ? problem : label + " " + DMY.format(date) + " không phải ngày học. Chọn ngày học khác.";
    }

    private static String range(SchoolHolidayItem holiday) {
        return holiday.startDate().equals(holiday.endDate())
                ? DMY.format(holiday.startDate())
                : DMY.format(holiday.startDate()) + " – " + DMY.format(holiday.endDate());
    }
}
