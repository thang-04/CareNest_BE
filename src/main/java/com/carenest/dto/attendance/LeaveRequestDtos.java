package com.carenest.dto.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Đơn nghỉ của phụ huynh (ATT-04, ATT-05, ATT-05b). */
public final class LeaveRequestDtos {

    private LeaveRequestDtos() {}

    public record CreateLeaveRequest(
            @NotNull Long childId,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @NotBlank @Size(max = 500) String reason) {}

    /**
     * status: ACTIVE / CANCELLED (hủy hết). effectiveTo: ngày cuối còn hiệu lực. appliedDates: ngày đã ghi Vắng có phép;
     * skippedDates: ngày đã qua giờ chốt khi gửi nên không đổi điểm danh (ATT-05b).
     */
    public record LeaveRequestResponse(
            Long id,
            Long childId,
            String childName,
            Long classId,
            String className,
            Long campusId,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate effectiveTo,
            String reason,
            String status,
            Long createdBy,
            String createdByName,
            Instant createdAt,
            Instant cancelledAt,
            List<LocalDate> appliedDates,
            List<LocalDate> skippedDates) {}

    /** Nhãn "Phụ huynh báo nghỉ" ở dòng trẻ của màn điểm danh. */
    public record LeaveTag(Long requestId, LocalDate fromDate, LocalDate toDate, String reason) {}
}
