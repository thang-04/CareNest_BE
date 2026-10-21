package com.carenest.service.organization;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.List;

/** Truy vấn phân công cho kiểm tra scope (security/authorization); không gọi ngược policy (MODULE_MAP). */
public interface StaffAssignmentService {

    boolean hasActivePrincipalAssignment(Long userId, LocalDate date);

    boolean hasActiveCampusAssignment(Long userId, StaffRole staffRole, Long campusId, LocalDate date);

    boolean hasActiveTeacherAssignment(Long userId, Long classroomId, LocalDate date);

    List<StaffAssignmentSummaryResponse> findActiveAssignments(Long userId, LocalDate date);
}
