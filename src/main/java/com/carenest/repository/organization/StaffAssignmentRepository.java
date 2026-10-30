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

    // Đã từng dạy lớp tới ngày :date (bỏ phân công gỡ trước khi bắt đầu: validTo < validFrom)
    @Query("""
            select case when count(a) > 0 then true else false end from StaffAssignment a
            where a.userId = :userId and a.staffRole = com.carenest.utils.enums.StaffRole.TEACHER
              and a.classroomId = :classroomId
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= a.validFrom)
            """)
    boolean existsPastTeacherForClassroom(
            @Param("userId") Long userId, @Param("classroomId") Long classroomId, @Param("date") LocalDate date);

    @Query("""
            select distinct a.ageGroupId from StaffAssignment a
            where a.userId = :userId and a.staffRole = com.carenest.utils.enums.StaffRole.AGE_GROUP_TEAM_LEADER
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)
            """)
    List<Long> findActiveTeamLeaderAgeGroupIds(@Param("userId") Long userId, @Param("date") LocalDate date);

    // Nhóm tuổi của các lớp GV đang dạy; lớp chưa gán nhóm tuổi thì bỏ qua
    @Query("""
            select distinct c.ageGroupId from StaffAssignment a, Classroom c
            where a.classroomId = c.id and a.userId = :userId
              and a.staffRole = com.carenest.utils.enums.StaffRole.TEACHER and c.ageGroupId is not null
              and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)
            """)
    List<Long> findActiveTeacherAgeGroupIds(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("""
            select distinct u.fullName from StaffAssignment a, UserAccount u
            where u.id = a.userId and a.campusId = :campusId
              and a.staffRole = com.carenest.utils.enums.StaffRole.VICE_PRINCIPAL
              and (a.validTo is null or a.validTo >= :date)
            order by u.fullName
            """)
    List<String> findOpenVicePrincipalNames(@Param("campusId") Long campusId, @Param("date") LocalDate date);

    @Query("select a from StaffAssignment a where a.userId = :userId"
            + " and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date) order by a.staffRole, a.id")
    List<StaffAssignment> findActiveByUserId(@Param("userId") Long userId, @Param("date") LocalDate date);

    // Lớp GV đang dạy hoặc sắp dạy (phân công chưa kết thúc tính tới :date)
    @Query("select distinct a.classroomId from StaffAssignment a where a.userId = :userId"
            + " and a.staffRole = com.carenest.utils.enums.StaffRole.TEACHER"
            + " and (a.validTo is null or a.validTo >= :date)")
    List<Long> findCurrentTeacherClassroomIds(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("select distinct a.userId from StaffAssignment a"
            + " where a.staffRole = com.carenest.utils.enums.StaffRole.AGE_GROUP_TEAM_LEADER"
            + " and a.ageGroupId = :ageGroupId and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)")
    List<Long> findActiveTeamLeaderIds(@Param("ageGroupId") Long ageGroupId, @Param("date") LocalDate date);

    // GV hiện tại của lớp: phân công chưa kết thúc tính tới ngày :date (gồm phân công bắt đầu sau, cho năm sắp tới)
    @Query("select a from StaffAssignment a where a.staffRole = com.carenest.utils.enums.StaffRole.TEACHER"
            + " and a.classroomId in :classroomIds and (a.validTo is null or a.validTo >= :date) order by a.id")
    List<StaffAssignment> findCurrentTeachers(
            @Param("classroomIds") Collection<Long> classroomIds, @Param("date") LocalDate date);

    @Query(
            "select a from StaffAssignment a where a.staffRole = com.carenest.utils.enums.StaffRole.AGE_GROUP_TEAM_LEADER"
                    + " and (a.schoolYearId = :schoolYearId or (a.schoolYearId is null and a.validFrom <= :yearEnd))"
                    + " and (a.validTo is null or a.validTo >= :from) order by a.id")
    List<StaffAssignment> findCurrentTeamLeaders(
            @Param("schoolYearId") Long schoolYearId,
            @Param("yearEnd") LocalDate yearEnd,
            @Param("from") LocalDate from);

    // Phương án C (tạm): ứng viên GV = người từng có phân công GV/tổ trưởng (gồm GV vừa được gỡ, để phân công lại);
    // điểm trường = điểm trường lớp của phân công gần nhất (dòng đầu theo valid_from giảm dần)
    @Query(
            value = "select u.id as userId, u.full_name as fullName, c.campus_id as campusId"
                    + " from staff_assignments a join user_accounts u on u.id = a.user_id"
                    + " left join classrooms c on c.id = a.classroom_id"
                    + " where a.staff_role in ('TEACHER', 'AGE_GROUP_TEAM_LEADER') and u.status = 'ACTIVE'"
                    + " order by u.id, a.valid_from desc, a.id desc",
            nativeQuery = true)
    List<TeacherRow> findTeacherRows();

    @Query("select a from StaffAssignment a where a.staffRole = com.carenest.utils.enums.StaffRole.TEACHER"
            + " and a.classroomId = :classroomId order by a.validFrom desc, a.id desc")
    List<StaffAssignment> findTeacherHistory(@Param("classroomId") Long classroomId);

    @Query(
            "select a from StaffAssignment a where a.staffRole = com.carenest.utils.enums.StaffRole.AGE_GROUP_TEAM_LEADER"
                    + " and a.ageGroupId = :ageGroupId"
                    + " and (a.schoolYearId = :schoolYearId or (a.schoolYearId is null and a.validFrom <= :yearEnd"
                    + " and (a.validTo is null or a.validTo >= :yearStart)))"
                    + " order by a.validFrom desc, a.id desc")
    List<StaffAssignment> findTeamLeaderHistory(
            @Param("ageGroupId") Long ageGroupId,
            @Param("schoolYearId") Long schoolYearId,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd);

    // Ứng viên PHT: người từng có phân công PHT (vai trò nằm ở Keycloak, chưa có API tài khoản để đọc)
    @Query(
            value = "select distinct u.id as userId, u.full_name as fullName, u.email as email, u.status as status"
                    + " from staff_assignments a join user_accounts u on u.id = a.user_id"
                    + " where a.staff_role = 'VICE_PRINCIPAL' order by u.full_name, u.id",
            nativeQuery = true)
    List<VicePrincipalRow> findVicePrincipalRows();

    // Phân công PHT giao với khoảng [from, to]; mới nhất trước
    @Query("select a from StaffAssignment a where a.staffRole = com.carenest.utils.enums.StaffRole.VICE_PRINCIPAL"
            + " and a.validFrom <= :to and (a.validTo is null or a.validTo >= :from)"
            + " order by a.validFrom desc, a.id desc")
    List<StaffAssignment> findVicePrincipalAssignments(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select a from StaffAssignment a where a.staffRole = com.carenest.utils.enums.StaffRole.VICE_PRINCIPAL"
            + " and a.userId = :userId and (a.validTo is null or a.validTo >= :from) order by a.validFrom, a.id")
    List<StaffAssignment> findOpenVicePrincipalAssignments(@Param("userId") Long userId, @Param("from") LocalDate from);

    // Tài khoản bị khóa / ngừng (đọc bảng user_accounts của module tài khoản, chỉ đọc)
    @Query(value = "select u.id from user_accounts u where u.id in :ids and u.status <> 'ACTIVE'", nativeQuery = true)
    List<Long> findInactiveUserIds(@Param("ids") Collection<Long> ids);

    interface VicePrincipalRow {
        Long getUserId();

        String getFullName();

        String getEmail();

        String getStatus();
    }

    interface TeacherRow {
        Long getUserId();

        String getFullName();

        Long getCampusId();
    }
}
