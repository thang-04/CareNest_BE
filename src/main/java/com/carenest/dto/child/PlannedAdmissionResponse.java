package com.carenest.dto.child;

import com.carenest.utils.enums.PlannedAdmissionStatus;
import java.time.LocalDate;

/** Đăng ký vào năm học sau của trẻ (CHD-09, CHD-11): năm, điểm trường, lớp (nếu đã chọn), ngày vào học, trạng thái. */
public record PlannedAdmissionResponse(
        Long schoolYearId,
        String schoolYearName,
        Long campusId,
        Long classId,
        String className,
        LocalDate startDate,
        PlannedAdmissionStatus status) {}
