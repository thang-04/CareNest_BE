package com.carenest.service.attendance;

import com.carenest.dto.attendance.DailyHealthEntryResponse;
import com.carenest.dto.attendance.DailyHealthRequest;
import com.carenest.dto.attendance.DailyHealthResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.HealthSymptom;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

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

    /** Biểu hiện của lần ghi nhận mới nhất trong ngày (không gồm thân nhiệt); chỉ dùng nội bộ, module gọi tự kiểm quyền. */
    record ChildDaySigns(Long childId, LocalDate date, List<HealthSymptom> signs, String note) {}

    /** Đánh giá trẻ điền sẵn ô Sức khỏe từ sức khỏe trong ngày (EDU-19). */
    List<ChildDaySigns> findLatestDaySigns(Collection<Long> childIds, LocalDate from, LocalDate to);

    /** Một lần ghi nhận sức khỏe kèm lớp và người ghi (chỉ dùng nội bộ, module gọi tự kiểm quyền). */
    record ChildHealthEntry(
            Long childId,
            Long classroomId,
            LocalDate date,
            BigDecimal temperature,
            List<HealthSymptom> symptoms,
            String note,
            Long recordedBy,
            Instant recordedAt) {}

    /** Lịch sử lớp cũ của trẻ chuyển đến (OBS-35): mọi lần ghi nhận trong khoảng, cũ trước. */
    List<ChildHealthEntry> findDayEntries(Collection<Long> childIds, LocalDate from, LocalDate to);
}
