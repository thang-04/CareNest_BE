package com.carenest.dto.child;

/** Lớp nhận trẻ khi tiếp nhận / xếp lớp: lớp đang hoạt động của năm học đang hoạt động (CHD-10). capacity null = không giới hạn. */
public record PlacementClassResponse(
        Long id,
        String name,
        Long campusId,
        Long ageGroupId,
        Long schoolYearId,
        Integer capacity,
        long enrolledCount,
        Long homeroomTeacherId) {}
