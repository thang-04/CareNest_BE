package com.carenest.dto.organization;

import java.util.List;

public record ClassTeachersResponse(
        Long id,
        String name,
        Long campusId,
        Long ageGroupId,
        long childCount,
        Integer capacity,
        List<Long> teacherIds,
        Long homeroomTeacherId,
        // Tên giáo viên kèm theo để năm đã kết thúc vẫn hiện đúng người (không phụ thuộc danh sách giáo viên hiện tại)
        List<Teacher> teachers) {

    public record Teacher(Long id, String fullName) {}
}
