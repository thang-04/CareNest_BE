package com.carenest.dto.organization;

import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;

/**
 * Dữ liệu tạo phân công đã qua kiểm quyền người làm ở feature account; service organization kiểm hình dạng, tồn tại,
 * trùng và luật xếp lớp GV (AUTH-24). assignedBy = người phân công (null khi hệ thống tự tạo); substitute = dạy thay; replacedTeacherId = dạy thay cho GV nào.
 */
public record NewStaffAssignment(
        Long userId,
        StaffRole staffRole,
        Long campusId,
        Long classroomId,
        LocalDate validFrom,
        LocalDate validTo,
        Long assignedBy,
        boolean substitute,
        Long replacedTeacherId) {

    public NewStaffAssignment(
            Long userId,
            StaffRole staffRole,
            Long campusId,
            Long classroomId,
            LocalDate validFrom,
            LocalDate validTo,
            Long assignedBy,
            boolean substitute) {
        this(userId, staffRole, campusId, classroomId, validFrom, validTo, assignedBy, substitute, null);
    }
}
