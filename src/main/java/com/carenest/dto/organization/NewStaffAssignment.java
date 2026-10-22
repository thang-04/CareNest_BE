package com.carenest.dto.organization;

import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;

/** Dữ liệu tạo phân công đã qua kiểm quyền ở feature account; service organization chỉ kiểm hình dạng/tồn tại/trùng. */
public record NewStaffAssignment(
        Long userId, StaffRole staffRole, Long campusId, Long classroomId, LocalDate validFrom, LocalDate validTo) {}
