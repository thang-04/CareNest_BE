package com.carenest.dto.organization;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Giờ chốt dạng HH:mm, áp dụng từ ngày effectiveFrom (trong năm học). latestAfternoonArrival: giờ đến muộn nhất buổi
 * chiều của trẻ nghỉ sáng (ATT-13), phải sau giờ chốt; bỏ trống = giữ giờ đang đặt (mặc định 14:00).
 */
public record CutoffRequest(
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime time,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 500) String note,
        @JsonFormat(pattern = "HH:mm") LocalTime latestAfternoonArrival) {

    public CutoffRequest(LocalTime time, LocalDate effectiveFrom, String note) {
        this(time, effectiveFrom, note, null);
    }
}
