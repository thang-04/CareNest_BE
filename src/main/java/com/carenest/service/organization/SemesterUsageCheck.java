package com.carenest.service.organization;

import java.time.LocalDate;

/**
 * Học kỳ đã có dữ liệu gắn theo khoảng ngày của nó (vd. đánh giá học kỳ — OBS-06) thì không đổi ngày học kỳ.
 * Module dùng học kỳ cài đặt interface này để năm học không phụ thuộc ngược vào module đó.
 */
public interface SemesterUsageCheck {

    /** Có dữ liệu thuộc học kỳ [start, end] của năm học không. */
    boolean usedBySemester(Long schoolYearId, LocalDate start, LocalDate end);

    /** Năm học đã có dữ liệu mở từ ngày kết thúc học kỳ II (đánh giá cuối năm, đề xuất khen thưởng) không. */
    default boolean usedByYearEnd(Long schoolYearId) {
        return false;
    }
}
