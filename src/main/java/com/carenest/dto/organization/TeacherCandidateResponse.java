package com.carenest.dto.organization;

import java.util.Set;

/**
 * Giáo viên có thể phân công; campusIds = điểm trường của các lớp đang dạy (rỗng nếu chỉ là tổ trưởng),
 * campusId = điểm trường chính để hiển thị (nhỏ nhất trong campusIds).
 */
public record TeacherCandidateResponse(Long id, String fullName, Long campusId, Set<Long> campusIds) {}
