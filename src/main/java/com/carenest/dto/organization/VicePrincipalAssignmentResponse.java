package com.carenest.dto.organization;

import java.time.LocalDate;
import java.util.List;

/** Phân công Phó hiệu trưởng của một năm học: mỗi PHT một dòng, assignment = null khi chưa phụ trách điểm trường. */
public record VicePrincipalAssignmentResponse(SchoolYearDetailResponse year, List<Row> rows) {

    public record Row(VicePrincipal user, Assignment assignment) {}

    public record VicePrincipal(Long id, String fullName, String email, String status) {}

    public record Assignment(Long campusId, LocalDate validFrom, LocalDate validTo, Long assignedBy) {}
}
