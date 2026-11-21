package com.carenest.dto.child;

import java.time.LocalDate;

/** Mở lại hồ sơ; có schoolYearId = mở cho năm sắp diễn ra (CHD-11), startDate trống = ngày bắt đầu năm. */
public record ReadmitChildRequest(String reason, Long schoolYearId, LocalDate startDate) {

    public ReadmitChildRequest(String reason) {
        this(reason, null, null);
    }
}
