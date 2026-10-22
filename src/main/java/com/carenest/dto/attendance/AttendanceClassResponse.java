package com.carenest.dto.attendance;

import com.carenest.utils.enums.MealSession;
import java.util.List;

/**
 * Lớp của năm học người dùng xem được. teaches = giáo viên được phân công lớp; canRecord = teaches và năm đang hoạt động;
 * canUnlock = HT/PHT điểm trường, năm đang hoạt động.
 */
public record AttendanceClassResponse(
        Long id,
        String name,
        Long campusId,
        Long ageGroupId,
        List<MealSession> mealSessions,
        boolean teaches,
        boolean canRecord,
        boolean canUnlock) {}
