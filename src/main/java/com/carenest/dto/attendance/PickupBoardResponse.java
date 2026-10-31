package com.carenest.dto.attendance;

import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.PickupStatus;
import java.time.LocalDate;
import java.util.List;

/** Bảng đón trẻ của lớp trong một ngày: chỉ trẻ có mặt chờ đón; {@code today} để biết có trả trẻ được không. */
public record PickupBoardResponse(
        Long classId, String className, LocalDate date, LocalDate today, List<PickupRow> rows) {

    /** {@code pickup}: lần trả thành công (null khi chưa); {@code attempts}: mọi lần xác minh trong ngày, cũ trước. */
    public record PickupRow(
            PickupChildRef child,
            AttendanceStatus attendanceStatus,
            PickupStatus status,
            PickupRecordResponse pickup,
            List<PickupRecordResponse> attempts) {}

    public record PickupChildRef(Long id, String code, String fullName, Gender gender) {}
}
