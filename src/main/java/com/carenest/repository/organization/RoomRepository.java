package com.carenest.repository.organization;

import com.carenest.entity.organization.Room;
import com.carenest.utils.enums.RoomStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByStatusNotOrderByCampusIdAscCodeAsc(RoomStatus status);

    List<Room> findAllByCampusIdInAndStatusNotOrderByCampusIdAscCodeAsc(Collection<Long> campusIds, RoomStatus status);

    @Query(
            "select count(r) > 0 from Room r where r.campusId = :campusId and lower(r.code) = lower(:code)"
                    + " and r.status <> com.carenest.utils.enums.RoomStatus.DELETED and (:excludeId is null or r.id <> :excludeId)")
    boolean existsCode(
            @Param("campusId") Long campusId, @Param("code") String code, @Param("excludeId") Long excludeId);

    @Query(
            "select count(r) > 0 from Room r where r.campusId = :campusId and lower(r.name) = lower(:name)"
                    + " and r.status <> com.carenest.utils.enums.RoomStatus.DELETED and (:excludeId is null or r.id <> :excludeId)")
    boolean existsName(
            @Param("campusId") Long campusId, @Param("name") String name, @Param("excludeId") Long excludeId);

    @Query("select r from Room r where r.campusId = :campusId"
            + " and r.status <> com.carenest.utils.enums.RoomStatus.DELETED"
            + " and (lower(r.code) = lower(:code) or lower(r.name) = lower(:name))"
            + " order by r.id")
    List<Room> findReusableByCodeOrName(
            @Param("campusId") Long campusId, @Param("code") String code, @Param("name") String name);

    long countByCampusIdAndStatusNot(Long campusId, RoomStatus status);

    // Người phụ trách phòng phải là nhân sự còn phân công ở điểm trường (trực tiếp hoặc qua lớp), hoặc Hiệu trưởng
    @Query(value = """
                    select count(*) > 0 from staff_assignments a
                    left join classrooms c on c.id = a.classroom_id
                    where a.user_id = :userId
                      and a.valid_from <= :date and (a.valid_to is null or a.valid_to >= :date)
                      and (a.staff_role = 'PRINCIPAL' or a.campus_id = :campusId or c.campus_id = :campusId)
                    """, nativeQuery = true)
    boolean isStaffOfCampus(
            @Param("userId") Long userId, @Param("campusId") Long campusId, @Param("date") LocalDate date);

    // Cần chốt 18/11/2026: phụ trách bếp chỉ là nhân viên bếp, PHT của điểm trường (còn phân công) hoặc Hiệu trưởng
    @Query(value = """
                    select count(*) > 0 from staff_assignments a
                    where a.user_id = :userId
                      and a.valid_from <= :date and (a.valid_to is null or a.valid_to >= :date)
                      and (a.staff_role = 'PRINCIPAL'
                           or (a.staff_role in ('KITCHEN_STAFF', 'VICE_PRINCIPAL') and a.campus_id = :campusId))
                    """, nativeQuery = true)
    boolean isKitchenManagerOfCampus(
            @Param("userId") Long userId, @Param("campusId") Long campusId, @Param("date") LocalDate date);

    @Query(
            value = "select u.id as userId, u.full_name as fullName from user_accounts u where u.id in :ids",
            nativeQuery = true)
    List<UserName> findUserNames(@Param("ids") Collection<Long> ids);

    // Người phụ trách phòng học = GV chủ nhiệm của lớp dùng phòng trong năm học
    @Query(
            value = "select c.room_id as roomId, u.id as userId, u.full_name as fullName from classrooms c"
                    + " join staff_assignments a on a.classroom_id = c.id and a.is_homeroom"
                    + " and (a.valid_to is null or a.valid_to >= :date)"
                    + " join user_accounts u on u.id = a.user_id"
                    + " where c.school_year_id = :schoolYearId and c.room_id in :roomIds",
            nativeQuery = true)
    List<RoomHomeroom> findHomeroomTeachers(
            @Param("roomIds") Collection<Long> roomIds,
            @Param("schoolYearId") Long schoolYearId,
            @Param("date") LocalDate date);

    interface RoomHomeroom {
        Long getRoomId();

        Long getUserId();

        String getFullName();
    }

    // Phòng / phòng ban theo năm học còn mở (V154): thêm, sửa, xóa ở năm nào chỉ năm đó
    @Query(
            value = "select room_id as roomId, campus_id as campusId, code, name, room_type as roomType, capacity,"
                    + " manager_user_id as managerUserId, status, note from school_year_room_members"
                    + " where school_year_id = :yearId",
            nativeQuery = true)
    List<YearRoomInfo> findYearRoomInfo(@Param("yearId") Long yearId);

    @Query(
            value = "select room_id as roomId, campus_id as campusId, code, name, room_type as roomType, capacity,"
                    + " manager_user_id as managerUserId, status, note from school_year_room_members"
                    + " where school_year_id = :yearId and room_id = :roomId",
            nativeQuery = true)
    Optional<YearRoomInfo> findYearRoomInfoById(@Param("yearId") Long yearId, @Param("roomId") Long roomId);

    @Query(
            value = "select count(*) > 0 from school_year_room_members where school_year_id = :yearId",
            nativeQuery = true)
    boolean hasYearRooms(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value = "insert into school_year_room_members (school_year_id, room_id, campus_id, code, name, room_type,"
                    + " capacity, manager_user_id, status, note) select :yearId, r.id, r.campus_id, r.code, r.name,"
                    + " r.room_type, r.capacity, r.manager_user_id, r.status, r.note from rooms r where r.id = :roomId"
                    + " on conflict do nothing",
            nativeQuery = true)
    void addToYear(@Param("yearId") Long yearId, @Param("roomId") Long roomId);

    @Modifying
    @Query(
            value = "insert into school_year_room_members (school_year_id, room_id, campus_id, code, name, room_type,"
                    + " capacity, manager_user_id, status, note) select :yearId, r.id, r.campus_id, r.code, r.name,"
                    + " r.room_type, r.capacity, r.manager_user_id, r.status, r.note from rooms r"
                    + " where r.deleted_at is null and r.status <> 'DELETED' on conflict do nothing",
            nativeQuery = true)
    void addAllToYear(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value = "insert into school_year_room_members (school_year_id, room_id, campus_id, code, name, room_type,"
                    + " capacity, manager_user_id, status, note) select :targetYearId, m.room_id, m.campus_id, m.code,"
                    + " m.name, m.room_type, m.capacity, m.manager_user_id, m.status, m.note"
                    + " from school_year_room_members m join rooms r on r.id = m.room_id"
                    + " where m.school_year_id = :sourceYearId and r.deleted_at is null on conflict do nothing",
            nativeQuery = true)
    int copyYearRooms(@Param("sourceYearId") Long sourceYearId, @Param("targetYearId") Long targetYearId);

    @Modifying
    @Query(
            value = "update school_year_room_members set campus_id = :campusId, code = :code, name = :name,"
                    + " room_type = :roomType, capacity = :capacity, manager_user_id = :managerUserId, note = :note"
                    + " where school_year_id = :yearId and room_id = :roomId",
            nativeQuery = true)
    int updateYearInfo(
            @Param("yearId") Long yearId,
            @Param("roomId") Long roomId,
            @Param("campusId") Long campusId,
            @Param("code") String code,
            @Param("name") String name,
            @Param("roomType") String roomType,
            @Param("capacity") Short capacity,
            @Param("managerUserId") Long managerUserId,
            @Param("note") String note);

    @Modifying
    @Query(
            value = "update school_year_room_members set status = :status"
                    + " where school_year_id = :yearId and room_id = :roomId",
            nativeQuery = true)
    int updateYearStatus(@Param("yearId") Long yearId, @Param("roomId") Long roomId, @Param("status") String status);

    @Modifying
    @Query(
            value = "delete from school_year_room_members where school_year_id = :yearId and room_id = :roomId",
            nativeQuery = true)
    void removeFromYear(@Param("yearId") Long yearId, @Param("roomId") Long roomId);

    @Query(value = "select count(*) from school_year_room_members where room_id = :roomId", nativeQuery = true)
    long countYears(@Param("roomId") Long roomId);

    @Query(
            value = "select count(*) > 0 from school_year_room_members where school_year_id = :yearId"
                    + " and campus_id = :campusId and lower(code) = lower(:code)"
                    + " and (:excludeId is null or room_id <> :excludeId)",
            nativeQuery = true)
    boolean existsCodeInYear(
            @Param("yearId") Long yearId,
            @Param("campusId") Long campusId,
            @Param("code") String code,
            @Param("excludeId") Long excludeId);

    @Query(
            value = "select count(*) > 0 from school_year_room_members where school_year_id = :yearId"
                    + " and campus_id = :campusId and lower(name) = lower(:name)"
                    + " and (:excludeId is null or room_id <> :excludeId)",
            nativeQuery = true)
    boolean existsNameInYear(
            @Param("yearId") Long yearId,
            @Param("campusId") Long campusId,
            @Param("name") String name,
            @Param("excludeId") Long excludeId);

    /** Kích hoạt năm: bảng chung nhận phòng của năm mới; phòng không có trong năm mới ngừng sử dụng. */
    @Modifying
    @Query(
            value = "update rooms r set campus_id = m.campus_id, code = m.code, name = m.name,"
                    + " room_type = m.room_type, capacity = m.capacity, manager_user_id = m.manager_user_id,"
                    + " status = m.status, note = m.note, updated_at = now()"
                    + " from school_year_room_members m where m.room_id = r.id and m.school_year_id = :yearId",
            nativeQuery = true)
    int applyYearInfo(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value = "update rooms r set status = 'INACTIVE', updated_at = now() where r.status = 'ACTIVE'"
                    + " and exists (select 1 from school_year_room_members where school_year_id = :yearId)"
                    + " and not exists (select 1 from school_year_room_members m"
                    + " where m.room_id = r.id and m.school_year_id = :yearId)",
            nativeQuery = true)
    int deactivateOutsideYear(@Param("yearId") Long yearId);

    interface YearRoomInfo {
        Long getRoomId();

        Long getCampusId();

        String getCode();

        String getName();

        String getRoomType();

        Short getCapacity();

        Long getManagerUserId();

        String getStatus();

        String getNote();
    }

    interface UserName {
        Long getUserId();

        String getFullName();
    }
}
