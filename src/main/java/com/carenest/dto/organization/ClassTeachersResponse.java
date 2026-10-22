package com.carenest.dto.organization;

import java.util.List;

public record ClassTeachersResponse(
        Long id,
        String name,
        Long campusId,
        Long ageGroupId,
        long childCount,
        List<Long> teacherIds,
        Long homeroomTeacherId) {}
