package com.carenest.service.organization;

import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Truy vấn phân công cho kiểm tra scope (security/authorization); không gọi ngược policy (MODULE_MAP). */
public interface StaffAssignmentService {

    boolean hasActivePrincipalAssignment(Long userId, LocalDate date);

    boolean hasActiveCampusAssignment(Long userId, StaffRole staffRole, Long campusId, LocalDate date);

    boolean hasActiveTeacherAssignment(Long userId, Long classroomId, LocalDate date);

    /** Đã từng được phân công dạy lớp (tới ngày date), kể cả phân công đã kết thúc. */
    boolean hasTeacherAssignmentHistory(Long userId, Long classroomId, LocalDate date);

    List<StaffAssignmentSummaryResponse> findActiveAssignments(Long userId, LocalDate date);

    /** Nhóm tuổi Tổ trưởng đang phụ trách (phân công AGE_GROUP_TEAM_LEADER còn hiệu lực). */
    Set<Long> findActiveTeamLeaderAgeGroupIds(Long userId, LocalDate date);

    /** Nhóm tuổi của các lớp giáo viên đang dạy. */
    Set<Long> findActiveTeacherAgeGroupIds(Long userId, LocalDate date);

    /** Lớp giáo viên đang hoặc sắp dạy (phân công chưa kết thúc tới ngày date). */
    Set<Long> findCurrentTeacherClassroomIds(Long userId, LocalDate date);

    /** Giáo viên hiện tại của từng lớp (gồm phân công bắt đầu sau ngày date). */
    Map<Long, List<Long>> findCurrentTeacherIds(Collection<Long> classroomIds, LocalDate date);

    /** Tổ trưởng đang phụ trách nhóm tuổi. */
    Set<Long> findActiveTeamLeaderIds(Long ageGroupId, LocalDate date);
}
