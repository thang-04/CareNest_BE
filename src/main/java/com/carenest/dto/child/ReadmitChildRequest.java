package com.carenest.dto.child;

import java.time.LocalDate;

/**
 * Mở lại hồ sơ; có schoolYearId = mở cho năm sắp diễn ra (CHD-11), startDate trống = ngày bắt đầu năm. campusId trống =
 * giữ điểm trường cũ; có giá trị = chuyển sang điểm trường của năm tiếp nhận (G94-180/CHD-02, user chốt 2026-11-22).
 */
public record ReadmitChildRequest(String reason, Long schoolYearId, LocalDate startDate, Long campusId) {

    public ReadmitChildRequest(String reason) {
        this(reason, null, null, null);
    }

    public ReadmitChildRequest(String reason, Long schoolYearId, LocalDate startDate) {
        this(reason, schoolYearId, startDate, null);
    }
}
