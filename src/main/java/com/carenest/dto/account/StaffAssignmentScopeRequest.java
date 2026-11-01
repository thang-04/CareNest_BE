package com.carenest.dto.account;

import java.time.LocalDate;

/**
 * Phạm vi kèm khi tạo tài khoản (AUTH-15): PHT/bếp ⇒ campusId, tạo phân công campus, validFrom bỏ trống ⇒ hôm nay.
 * GV ⇒ campusId chỉ để kiểm PHT tạo trong campus mình, không tạo phân công; phân công lớp làm sau.
 * Hiệu trưởng tự có phân công toàn trường nên không gửi phần này.
 */
public record StaffAssignmentScopeRequest(Long campusId, LocalDate validFrom) {}
