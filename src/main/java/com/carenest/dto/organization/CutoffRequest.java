package com.carenest.dto.organization;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Giờ chốt dạng HH:mm, áp dụng từ ngày effectiveFrom (trong năm học). latestAfternoonArrival: giờ đến muộn nhất buổi
 * chiều của trẻ nghỉ sáng (ATT-13), sau giờ chốt; bỏ trống = giữ giờ đang đặt (mặc định 14:00). Thứ tự các mốc theo
 * PCK-04 (phương án A), sai thứ tự trả lỗi theo từng ô.
 */
public record CutoffRequest(
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime time,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 500) String note,
        @JsonFormat(pattern = "HH:mm") LocalTime latestAfternoonArrival,
        @JsonFormat(pattern = "HH:mm") LocalTime dropoffOpenFrom,
        @JsonFormat(pattern = "HH:mm") LocalTime morningStart,
        @JsonFormat(pattern = "HH:mm") LocalTime morningEnd,
        @JsonFormat(pattern = "HH:mm") LocalTime afternoonStart,
        @JsonFormat(pattern = "HH:mm") LocalTime pickupStart,
        @JsonFormat(pattern = "HH:mm") LocalTime fullDayPickupFrom,
        @JsonFormat(pattern = "HH:mm") LocalTime pickupEnd,
        @JsonFormat(pattern = "HH:mm") LocalTime afternoonEnd) {

    public CutoffRequest(LocalTime time, LocalDate effectiveFrom, String note) {
        this(time, effectiveFrom, note, null, null, null, null, null, null, null, null, null);
    }

    public CutoffRequest(LocalTime time, LocalDate effectiveFrom, String note, LocalTime latestAfternoonArrival) {
        this(time, effectiveFrom, note, latestAfternoonArrival, null, null, null, null, null, null, null, null);
    }
}
