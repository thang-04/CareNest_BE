package com.carenest.repository.organization;

import com.carenest.entity.organization.StaffAssignment;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Mọi query lọc theo ngày: chỉ phân công có hiệu lực tại {@code date} mới tạo phạm vi. */
public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {

    @Query("select a from StaffAssignment a where a.staffRole in :roles and a.validFrom <= :date"
            + " and (a.validTo is null or a.validTo >= :date) order by a.userId, a.id")
    List<StaffAssignment> findActiveByRoles(@Param("roles") Collection<StaffRole> roles, @Param("date") LocalDate date);

    // AUTH-24: khóa phân công GV của lớp / của GV trong năm học tới hết transaction (mọi đường phân công dùng chung)
    @Query(
            value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(concat('carenest:class-teachers:',"
                    + " CAST(:classId AS text)), 0))",
            nativeQuery = true)
    Integer lockClassTeachers(@Param("classId") Long classId);

    @Query(
            value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(concat('carenest:teacher-year:',"
                    + " CAST(:teacherId AS text), ':', CAST(:yearId AS text)), 0))",
            nativeQuery = true)
    Integer lockTeacherYear(@Param("teacherId") Long teacherId, @Param("yearId") Long yearId);

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

    // Nhóm tuổi Tổ trưởng phụ trách trong một năm học (kể cả năm sắp diễn ra), phân công chưa kết thúc tính tới :date
    @Query("select distinct a.ageGroupId from StaffAssignment a where a.userId = :userId"
            + " and a.staffRole = com.carenest.utils.enums.StaffRole.AGE_GROUP_TEAM_LEADER"
            + " and a.schoolYearId = :schoolYearId and (a.validTo is null or a.validTo >= :date)")
    List<Long> findYearTeamLeaderAgeGroupIds(
            @Param("userId") Long userId, @Param("schoolYearId") Long schoolYearId, @Param("date") LocalDate date);

    @Query("select distinct a.userId from StaffAssignment a"
            + " where a.staffRole = com.carenest.utils.enums.StaffRole.AGE_GROUP_TEAM_LEADER"
            + " and a.ageGroupId = :ageGroupId and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)")
    List<Long> findActiveTeamLeaderIds(@Param("ageGroupId") Long ageGroupId, @Param("date") LocalDate date);

    // Người đang giữ một vai trò quản lý: PHT của campus (campusId) hoặc HT (campusId null)
    @Query("select distinct a.userId from StaffAssignment a where a.staffRole = :role"
            + " and (:campusId is null or a.campusId = :campusId)"
            + " and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)")
    List<Long> findActiveLeaderIds(
            @Param("role") StaffRole role, @Param("campusId") Long campusId, @Param("date") LocalDate date);

    // Dạy thay: người được thay đang dạy chính lớp này vào ngày bắt đầu dạy thay
    @Query("select case when count(a) > 0 then true else false end from StaffAssignment a"
            + " where a.staffRole = com.carenest.utils.enums.StaffRole.TEACHER and a.substitute = false"
            + " and a.classroomId = :classroomId and a.userId = :userId"
            + " and a.validFrom <= :date and (a.validTo is null or a.validTo >= :date)")
    boolean existsMainTeacherOn(
            @Param("classroomId") Long classroomId, @Param("userId") Long userId, @Param("date") LocalDate date);

    // Dạy thay: đã có người dạy thay cho cùng GV ở lớp này trùng khoảng [from, to]
    @Query("select case when count(a) > 0 then true else false end from StaffAssignment a"
            + " where a.substitute = true and a.classroomId = :classroomId and a.replacedUserId = :replacedUserId"
            + " and a.validFrom <= :to and (a.validTo is null or a.validTo >= :from)")
    boolean existsSubstituteFor(
            @Param("classroomId") Long classroomId,
            @Param("replacedUserId") Long replacedUserId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

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

    // Phương án C (tạm): ứng viên GV = người từng có phân công GV/tổ trưởng (gồm GV vừa được gỡ, để phân công lại)
    // và tài khoản GV chưa có phân công (tạo không kèm lớp — AUTH-15); điểm trường = điểm trường lớp của phân công
    // gần nhất (dòng đầu theo valid_from giảm dần), null nếu chưa dạy lớp nào
    @Query(
            value = "select u.id as userId, u.full_name as fullName, c.campus_id as campusId"
                    + " from user_accounts u"
                    + " left join staff_assignments a on a.user_id = u.id"
                    + " and a.staff_role in ('TEACHER', 'AGE_GROUP_TEAM_LEADER')"
                    + " left join classrooms c on c.id = a.classroom_id"
                    + " where u.status = 'ACTIVE' and (a.id is not null or u.primary_role = 'TEACHER')"
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

    // AUTH-26: PHT khác đã phụ trách điểm trường trong khoảng [from, to] ⇒ không xếp thêm người
    @Query("""
            select case when count(a) > 0 then true else false end from StaffAssignment a
            where a.staffRole = com.carenest.utils.enums.StaffRole.VICE_PRINCIPAL
              and a.campusId = :campusId and a.userId <> :userId
              and a.validFrom <= :to and (a.validTo is null or a.validTo >= :from)
            """)
    boolean existsOtherVicePrincipalAtCampus(
            @Param("campusId") Long campusId,
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // Phân công không ngày kết thúc ⇒ so tới ngày xa nhất PostgreSQL date nhận được, tránh tham số null trong query
    default boolean existsOtherVicePrincipalAtCampusOpenEnded(
            Long campusId, Long userId, LocalDate from, LocalDate to) {
        return existsOtherVicePrincipalAtCampus(campusId, userId, from, to == null ? LocalDate.of(9999, 12, 31) : to);
    }

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

    // Phân công cùng người, cùng role, cùng phạm vi giao với khoảng [validFrom, validTo] ⇒ trùng
    // (xét cả hai đầu để đợt trước không bị chặn bởi đợt sau; bỏ phân công gỡ trước khi bắt đầu)
    @Query("""
            select case when count(a) > 0 then true else false end from StaffAssignment a
            where a.userId = :userId and a.staffRole = :staffRole
              and a.campusId is not distinct from :campusId and a.classroomId is not distinct from :classroomId
              and a.validFrom <= :validTo and (a.validTo is null or a.validTo >= :validFrom)
              and (a.validTo is null or a.validTo >= a.validFrom)
            """)
    boolean existsOverlapping(
            @Param("userId") Long userId,
            @Param("staffRole") StaffRole staffRole,
            @Param("campusId") Long campusId,
            @Param("classroomId") Long classroomId,
            @Param("validFrom") LocalDate validFrom,
            @Param("validTo") LocalDate validTo);

    // Phân công mới không ngày kết thúc ⇒ so tới ngày xa nhất PostgreSQL date nhận được, tránh tham số null trong query
    default boolean existsOverlappingOpenEnded(
            Long userId, StaffRole staffRole, Long campusId, Long classroomId, LocalDate validFrom, LocalDate validTo) {
        return existsOverlapping(
                userId,
                staffRole,
                campusId,
                classroomId,
                validFrom,
                validTo == null ? LocalDate.of(9999, 12, 31) : validTo);
    }

    // Phân công gắn với năm học (lớp của năm, tổ trưởng / PHT ghi school_year_id) theo ngày năm học khi đổi ngày năm
    @Modifying
    @Query(
            value = "UPDATE staff_assignments a SET valid_to = :newEnd, updated_at = now()"
                    + " WHERE (a.school_year_id = :yearId OR a.classroom_id IN"
                    + " (SELECT c.id FROM classrooms c WHERE c.school_year_id = :yearId))"
                    + " AND (a.valid_to = :oldEnd OR a.valid_to > :newEnd) AND a.valid_from <= :newEnd",
            nativeQuery = true)
    int realignYearEnd(
            @Param("yearId") Long yearId, @Param("oldEnd") LocalDate oldEnd, @Param("newEnd") LocalDate newEnd);

    @Modifying
    @Query(
            value = "UPDATE staff_assignments a SET valid_from = :newStart, updated_at = now()"
                    + " WHERE (a.school_year_id = :yearId OR a.classroom_id IN"
                    + " (SELECT c.id FROM classrooms c WHERE c.school_year_id = :yearId))"
                    + " AND (a.valid_from = :oldStart OR a.valid_from < :newStart)"
                    + " AND (a.valid_to IS NULL OR a.valid_to >= :newStart)",
            nativeQuery = true)
    int realignYearStart(
            @Param("yearId") Long yearId, @Param("oldStart") LocalDate oldStart, @Param("newStart") LocalDate newStart);
}
