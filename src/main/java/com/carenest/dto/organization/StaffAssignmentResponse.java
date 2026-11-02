package com.carenest.dto.organization;

import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;

/** Một phân công (kể cả đã kết thúc); validTo null = đang hiệu lực không thời hạn; substitute = GV dạy thay (AUTH-24). */
public record StaffAssignmentResponse(
        Long id,
        Long userId,
        StaffRole staffRole,
        Long campusId,
        Long classroomId,
        LocalDate validFrom,
        LocalDate validTo,
        boolean substitute) {}
