package com.carenest.repository.organization;

import com.carenest.entity.organization.Room;
import com.carenest.utils.enums.RoomStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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

    interface UserName {
        Long getUserId();

        String getFullName();
    }
}
