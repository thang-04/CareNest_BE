package com.carenest.dto.account;

import java.time.LocalDate;

/**
 * Phân công kèm khi tạo tài khoản (AUTH-15): PHT/bếp ⇒ campusId, GV ⇒ classroomId. validFrom bỏ trống ⇒ hôm nay.
 * Hiệu trưởng tự có phân công toàn trường nên không gửi phần này.
 */
public record StaffAssignmentScopeRequest(Long campusId, Long classroomId, LocalDate validFrom) {}
