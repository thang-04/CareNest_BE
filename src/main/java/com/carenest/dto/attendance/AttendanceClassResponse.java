package com.carenest.dto.attendance;

import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.List;

/**
 * Lớp của năm học người dùng xem được. teaches = giáo viên được phân công lớp; canRecord = teaches và năm đang hoạt động;
 * canUnlock = HT/PHT điểm trường, năm đang hoạt động.
 * AUTH-03: readRanges = khoảng dạy cũ của GV (chỉ xem) khi lớp không còn phân công; rỗng với lớp đang dạy.
 */
public record AttendanceClassResponse(
        Long id,
        String name,
        Long campusId,
        Long ageGroupId,
        List<MealSession> mealSessions,
        boolean teaches,
        boolean canRecord,
        boolean canUnlock,
        List<ReadRange> readRanges) {
    public record ReadRange(LocalDate start, LocalDate end) {}
}
