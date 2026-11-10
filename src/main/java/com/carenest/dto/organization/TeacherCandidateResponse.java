package com.carenest.dto.organization;

import java.util.Set;

/**
 * Giáo viên có thể phân công; campusIds = điểm trường của lớp phân công gần nhất (rỗng nếu chưa dạy lớp nào: GV mới
 * tạo hoặc chỉ là tổ trưởng ⇒ xếp được vào lớp mọi điểm trường), campusId = điểm trường đó để hiển thị.
 */
public record TeacherCandidateResponse(Long id, String fullName, Long campusId, Set<Long> campusIds) {}
