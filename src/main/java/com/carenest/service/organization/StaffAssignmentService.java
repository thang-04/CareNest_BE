package com.carenest.service.organization;

import com.carenest.dto.organization.NewStaffAssignment;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Phân công nhân sự: truy vấn cho kiểm tra scope (security/authorization) và ghi phân công. Kiểm quyền người thao tác
 * nằm ở feature account (StaffAccessPolicy); service này không gọi ngược policy (MODULE_MAP).
 */
public interface StaffAssignmentService {

    boolean hasActivePrincipalAssignment(Long userId, LocalDate date);

    /** Phân công tổ trưởng nhóm tuổi còn hiệu lực (AUTH-23). */
    boolean hasActiveTeamLeaderAssignment(Long userId, LocalDate date);

    boolean hasActiveCampusAssignment(Long userId, StaffRole staffRole, Long campusId, LocalDate date);

    boolean hasActiveTeacherAssignment(Long userId, Long classroomId, LocalDate date);

    List<StaffAssignmentSummaryResponse> findActiveAssignments(Long userId, LocalDate date);

    /** Campus mà phân công đang hiệu lực của role đó trỏ tới (GV: campus của lớp). */
    Set<Long> findActiveCampusIds(Long userId, StaffRole staffRole, LocalDate date);

    /** Người có phân công PHT, GV hoặc bếp đang hiệu lực trong các campus. */
    Set<Long> findActiveStaffUserIds(Collection<Long> campusIds, LocalDate date);

    Map<Long, List<StaffAssignmentSummaryResponse>> findActiveAssignmentsByUserIds(
            Collection<Long> userIds, LocalDate date);

    /** Mọi phân công của một người (kể cả đã kết thúc), mới nhất trước. */
    List<StaffAssignmentResponse> findAssignments(Long userId);

    Optional<StaffAssignmentResponse> findAssignment(Long assignmentId);

    /** Kiểm hình dạng theo role (V2 CHECK) và sự tồn tại campus/lớp; trả campus của phạm vi (null với PRINCIPAL). */
    Long resolveCampusId(StaffRole staffRole, Long campusId, Long classroomId);

    StaffAssignmentResponse createAssignment(NewStaffAssignment assignment);

    StaffAssignmentResponse endAssignment(Long assignmentId, LocalDate lastDay);
}
