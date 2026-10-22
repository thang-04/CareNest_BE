package com.carenest.dto.organization;

import com.carenest.utils.enums.ClassroomStatus;

/**
 * Lớp kèm số liệu: trẻ đang học, trẻ dự kiến (năm PLANNED: đã xếp vào lớp từ năm cũ, null với năm khác), số giáo viên còn thiếu (mỗi lớp 2 GV), mức đầy lớp OPEN/NEAR/FULL (≥ 90% là NEAR).
 */
public record ClassResponse(
        Long id,
        String name,
        Long schoolYearId,
        Long campusId,
        Long ageGroupId,
        Long locationId,
        Integer capacity,
        ClassroomStatus status,
        long childCount,
        Long plannedCount,
        int missingTeachers,
        boolean noRoom,
        String fill) {}
