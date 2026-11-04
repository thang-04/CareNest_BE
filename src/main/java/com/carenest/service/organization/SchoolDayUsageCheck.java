package com.carenest.service.organization;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Ngày đã có dữ liệu theo ngày học (vd. điểm danh) thì không đặt thành ngày nghỉ (SCH-05). Module dùng ngày học cài
 * đặt interface này để năm học không phụ thuộc ngược vào module đó.
 */
public interface SchoolDayUsageCheck {

    /** Ngày sớm nhất trong [from, to] đã có dữ liệu; rỗng nếu không có. */
    Optional<LocalDate> firstUsedDay(LocalDate from, LocalDate to);
}
