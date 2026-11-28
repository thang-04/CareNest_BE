package com.carenest.dto.eduplan;

import java.time.LocalDate;
import java.util.List;

/** Hoạt động dạy học thứ Hai–thứ Sáu của lớp, chỉ từ kế hoạch đã ban hành (App giáo viên). */
public record ClassTeachingActivitiesResponse(
        Long classId, LocalDate weekStart, boolean published, List<TeachingDay> days) {

    /** {@code published} false: ngày chưa có giáo án ban hành. */
    public record TeachingDay(LocalDate date, boolean published, List<ApprovedDayLessonResponse.Activity> activities) {}
}
