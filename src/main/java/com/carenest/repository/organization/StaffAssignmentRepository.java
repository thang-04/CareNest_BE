package com.carenest.repository.organization;

import com.carenest.entity.organization.StaffAssignment;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Mọi query lọc theo ngày: chỉ phân công có hiệu lực tại {@code date} mới tạo phạm vi. */
public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {

    @Query("""
            select case when count(a) > 0 then true else false end from StaffAssignment a
            where a.userId = :userId and a.staffRole = :staffRole
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)
            """)
    boolean existsActiveByRole(
            @Param("userId") Long userId, @Param("staffRole") StaffRole staffRole, @Param("date") LocalDate date);

    // Native vì phân công tổ trưởng (AGE_GROUP_TEAM_LEADER) do module education-plan thêm vào bảng, chưa có trong
    // enum StaffRole của module này
    @Query(value = """
            select exists (select 1 from staff_assignments a
            where a.user_id = :userId and a.staff_role = 'AGE_GROUP_TEAM_LEADER'
              and a.valid_from <= :date and (a.valid_to is null or a.valid_to >= :date))
            """, nativeQuery = true)
    boolean existsActiveTeamLeader(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("""
            select case when count(a) > 0 then true else false end from StaffAssignment a
            where a.userId = :userId and a.staffRole = :staffRole and a.campusId = :campusId
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)
            """)
    boolean existsActiveForCampus(
            @Param("userId") Long userId,
            @Param("staffRole") StaffRole staffRole,
            @Param("campusId") Long campusId,
            @Param("date") LocalDate date);

    @Query("""
            select case when count(a) > 0 then true else false end from StaffAssignment a
            where a.userId = :userId and a.staffRole = com.carenest.utils.enums.StaffRole.TEACHER
              and a.classroomId = :classroomId
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)
            """)
    boolean existsActiveTeacherForClassroom(
            @Param("userId") Long userId, @Param("classroomId") Long classroomId, @Param("date") LocalDate date);

    @Query("select a from StaffAssignment a where a.userId = :userId"
            + " and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date) order by a.staffRole, a.id")
    List<StaffAssignment> findActiveByUserId(@Param("userId") Long userId, @Param("date") LocalDate date);

    List<StaffAssignment> findByUserIdOrderByValidFromDescIdDesc(Long userId);

    @Query("select a from StaffAssignment a where a.userId in :userIds"
            + " and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date) order by a.staffRole, a.id")
    List<StaffAssignment> findActiveByUserIdIn(
            @Param("userIds") Collection<Long> userIds, @Param("date") LocalDate date);

    // Campus của phân công campus (PHT/bếp) theo role
    @Query("select distinct a.campusId from StaffAssignment a where a.userId = :userId and a.staffRole = :staffRole"
            + " and a.campusId is not null and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)")
    List<Long> findActiveCampusIds(
            @Param("userId") Long userId, @Param("staffRole") StaffRole staffRole, @Param("date") LocalDate date);

    // Campus của các lớp giáo viên đang được phân công
    @Query("select distinct c.campusId from StaffAssignment a, Classroom c where a.classroomId = c.id"
            + " and a.userId = :userId and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)")
    List<Long> findActiveTeacherCampusIds(@Param("userId") Long userId, @Param("date") LocalDate date);

    // PHT và bếp có phân công campus
    @Query("select distinct a.userId from StaffAssignment a where a.campusId in :campusIds"
            + " and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)")
    List<Long> findActiveCampusStaffUserIds(
            @Param("campusIds") Collection<Long> campusIds, @Param("date") LocalDate date);

    @Query("select distinct a.userId from StaffAssignment a, Classroom c where a.classroomId = c.id"
            + " and c.campusId in :campusIds and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)")
    List<Long> findActiveTeacherUserIds(@Param("campusIds") Collection<Long> campusIds, @Param("date") LocalDate date);

    // Phân công cùng người, cùng role, cùng phạm vi chưa kết thúc trước ngày bắt đầu mới ⇒ trùng
    @Query("""
            select case when count(a) > 0 then true else false end from StaffAssignment a
            where a.userId = :userId and a.staffRole = :staffRole
              and a.campusId is not distinct from :campusId and a.classroomId is not distinct from :classroomId
              and (a.validTo is null or a.validTo >= :validFrom)
            """)
    boolean existsOverlapping(
            @Param("userId") Long userId,
            @Param("staffRole") StaffRole staffRole,
            @Param("campusId") Long campusId,
            @Param("classroomId") Long classroomId,
            @Param("validFrom") LocalDate validFrom);
}
