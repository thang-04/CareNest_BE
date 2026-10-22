package com.carenest.service.attendance;

import com.carenest.dto.attendance.DailyHealthEntryResponse;
import com.carenest.dto.attendance.DailyHealthRequest;
import com.carenest.dto.attendance.DailyHealthResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;

/**
 * Sức khỏe trong ngày: giáo viên lớp ghi nhận khi tiếp nhận trẻ đã lưu Có mặt (Cần chốt 15–16/10/2026);
 * Ban giám hiệu xem tổng hợp trong phạm vi. Giáo viên quan sát, không chẩn đoán.
 */
public interface DailyHealthService {
    DailyHealthResponse.ChildDay getClassChildHealth(
            CurrentUser currentUser, Long classId, Long childId, LocalDate date);

    DailyHealthEntryResponse addEntry(CurrentUser currentUser, DailyHealthRequest request);

    DailyHealthResponse.Overview getOverview(
            CurrentUser currentUser, LocalDate date, Long schoolYearId, Long campusId, Long ageGroupId, Long classId);

    DailyHealthResponse.ChildDay getChildHistory(CurrentUser currentUser, Long childId, LocalDate date);
}
