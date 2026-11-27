package com.carenest.service.organization;

import com.carenest.dto.organization.CutoffRequest;
import com.carenest.dto.organization.CutoffResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/** Cấu hình trường › Giờ chốt điểm danh (ATT-06). Module điểm danh/suất ăn đọc giờ chốt qua findCutoffTime. */
public interface AttendanceCutoffService {

    CutoffResponse getCutoff(Long schoolYearId);

    /** Giờ chốt của năm cho người xem; năm chưa tới ngày mở chuẩn bị chỉ HT thấy (CFG-YR-05). */
    CutoffResponse getCutoff(CurrentUser currentUser, Long schoolYearId);

    CutoffResponse saveCutoff(CurrentUser currentUser, Long schoolYearId, CutoffRequest request);

    Optional<LocalTime> findCutoffTime(Long schoolYearId, LocalDate date);

    /** ATT-13: giờ đến muộn nhất buổi chiều của trẻ nghỉ sáng áp dụng ngày date. */
    Optional<LocalTime> findLatestAfternoonArrival(Long schoolYearId, LocalDate date);

    /** ATT-05b: giờ bắt đầu buổi chiều; báo nghỉ chiều hôm nay chỉ nhận trước giờ này. */
    Optional<LocalTime> findAfternoonStart(Long schoolYearId, LocalDate date);

    /** PCK-04: từ mốc này trẻ đã có mặt buổi sáng được tính đủ một ngày khi trả trẻ. */
    Optional<LocalTime> findFullDayPickupFrom(Long schoolYearId, LocalDate date);
}
