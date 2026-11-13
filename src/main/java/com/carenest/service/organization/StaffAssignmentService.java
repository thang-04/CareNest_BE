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

    Map<Long, List<StaffAssignmentSummaryResponse>> findActiveEducationReviewerAssignments(LocalDate date);

    boolean hasActivePrincipalAssignment(Long userId, LocalDate date);

    /** Phân công tổ trưởng nhóm tuổi còn hiệu lực (AUTH-23). */
    boolean hasActiveTeamLeaderAssignment(Long userId, LocalDate date);

    boolean hasActiveCampusAssignment(Long userId, StaffRole staffRole, Long campusId, LocalDate date);

    boolean hasActiveTeacherAssignment(Long userId, Long classroomId, LocalDate date);

    /** Đã từng được phân công dạy lớp (tới ngày date), kể cả phân công đã kết thúc. */
    boolean hasTeacherAssignmentHistory(Long userId, Long classroomId, LocalDate date);

    List<StaffAssignmentSummaryResponse> findActiveAssignments(Long userId, LocalDate date);

    /** Nhóm tuổi Tổ trưởng đang phụ trách (phân công AGE_GROUP_TEAM_LEADER còn hiệu lực). */
    Set<Long> findActiveTeamLeaderAgeGroupIds(Long userId, LocalDate date);

    /** Nhóm tuổi người dùng làm Tổ trưởng trong năm học; schoolYearId null ⇒ mọi năm (như bản không có năm). */
    Set<Long> findActiveTeamLeaderAgeGroupIds(Long userId, Long schoolYearId, LocalDate date);

    /**
     * Nhóm tuổi Tổ trưởng phụ trách trong năm học schoolYearId, gồm cả phân công năm sắp diễn ra chưa tới ngày bắt
     * đầu (giai đoạn chuẩn bị, CFG-YR-05).
     */
    Set<Long> findYearTeamLeaderAgeGroupIds(Long userId, Long schoolYearId, LocalDate date);

    /** Nhóm tuổi của các lớp giáo viên đang dạy. */
    Set<Long> findActiveTeacherAgeGroupIds(Long userId, LocalDate date);

    /** Lớp giáo viên đang hoặc sắp dạy (phân công chưa kết thúc tới ngày date). */
    Set<Long> findCurrentTeacherClassroomIds(Long userId, LocalDate date);

    /** Giáo viên hiện tại của từng lớp (gồm phân công bắt đầu sau ngày date). */
    Map<Long, List<Long>> findCurrentTeacherIds(Collection<Long> classroomIds, LocalDate date);

    /** Giáo viên chủ nhiệm hiện tại của từng lớp (lớp chưa có chủ nhiệm không có trong map). */
    Map<Long, Long> findHomeroomTeacherIds(Collection<Long> classroomIds, LocalDate date);

    /** Tổ trưởng đang phụ trách nhóm tuổi trong năm học (năm sau có Tổ trưởng riêng, không gộp). */
    Set<Long> findActiveTeamLeaderIds(Long schoolYearId, Long ageGroupId, LocalDate date);

    /**
     * Nhóm tuổi người dùng làm Tổ trưởng trong năm học schoolYearId (null = năm đang hoạt động): năm chưa kết thúc
     * cần phân công còn hiệu lực tới today; năm đã kết thúc tính mọi phân công của năm đó (chỉ xem lại).
     */
    Set<Long> findScopedTeamLeaderAgeGroupIds(Long userId, Long schoolYearId, LocalDate today);

    /** Tổ trưởng của nhóm tuổi trong đúng năm học, còn hiệu lực ngày date. */
    Set<Long> findYearTeamLeaderIds(Long ageGroupId, Long schoolYearId, LocalDate date);

    /** Campus mà phân công đang hiệu lực của role đó trỏ tới (GV: campus của lớp). */
    Set<Long> findActiveCampusIds(Long userId, StaffRole staffRole, LocalDate date);

    /** PHT đang phân công tại campus; không có thì HT (người xác nhận thay, NUT-02). */
    Set<Long> findCampusConfirmerIds(Long campusId, LocalDate date);

    /** PHT đang được phân công ở điểm trường tại ngày đó (không thay bằng HT khi trống). */
    Set<Long> findCampusVicePrincipalIds(Long campusId, LocalDate date);

    /** HT đang có phân công tại ngày đó. */
    Set<Long> findPrincipalIds(LocalDate date);

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

    /** Đổi ngày năm học: phân công theo ngày đầu / cuối năm cũ chuyển theo ngày mới, phân công vượt năm mới bị cắt. */
    void realignYearAssignments(
            Long schoolYearId, LocalDate oldStart, LocalDate oldEnd, LocalDate newStart, LocalDate newEnd);
}
