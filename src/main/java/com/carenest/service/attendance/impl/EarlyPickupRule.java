package com.carenest.service.attendance.impl;

import com.carenest.entity.organization.AttendanceCutoff;
import com.carenest.service.organization.AttendanceCutoffService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** PCK-04: một chỗ quyết định trả trẻ có trước mốc tính đủ ngày hay không (đón trẻ và lưu lại điểm danh dùng chung). */
@Component
@RequiredArgsConstructor
class EarlyPickupRule {

    private final AttendanceCutoffService attendanceCutoffService;

    /** Trả trước mốc tính đủ ngày đang hiệu lực của ngày đó ⇒ chỉ tính 0,5 ngày; đúng mốc trở đi tính đủ ngày. */
    boolean beforeFullDay(Long schoolYearId, LocalDate date, LocalTime handoverTime) {
        return beforeFullDayOn(schoolYearId, date).test(handoverTime);
    }

    /** Như {@link #beforeFullDay} nhưng đọc mốc một lần cho nhiều lần trả trẻ cùng ngày. */
    Predicate<LocalTime> beforeFullDayOn(Long schoolYearId, LocalDate date) {
        LocalTime fullDayFrom = attendanceCutoffService
                .findFullDayPickupFrom(schoolYearId, date)
                .orElse(AttendanceCutoff.DEFAULT_FULL_DAY_PICKUP_FROM);
        return handoverTime -> handoverTime.isBefore(fullDayFrom);
    }
}
