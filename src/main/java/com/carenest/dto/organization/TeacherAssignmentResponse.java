package com.carenest.dto.organization;

import java.util.List;

/** Màn Phân công giáo viên: lớp của năm học (trong phạm vi người xem) và giáo viên có thể chọn. */
public record TeacherAssignmentResponse(
        SchoolYearDetailResponse year,
        List<AgeGroupDetailResponse> ageGroups,
        List<ClassTeachersResponse> classes,
        List<TeacherCandidateResponse> teachers) {}
