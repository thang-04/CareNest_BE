package com.carenest.dto.child;

import com.carenest.utils.enums.ClassroomStatus;
import java.util.List;

/**
 * Lớp của trẻ ở hồ sơ ({@code cls}, plan tiếp nhận mục 4.6): lớp đang học; trẻ đã nghỉ là lớp cuối cùng; trẻ chờ xếp
 * lớp không có (null). {@code teacherIds} là GV đang phân công hôm nay.
 */
public record ChildClassResponse(
        Long id,
        String name,
        Long campusId,
        Long ageGroupId,
        Long schoolYearId,
        Integer capacity,
        ClassroomStatus status,
        Long homeroomTeacherId,
        List<Long> teacherIds) {}
