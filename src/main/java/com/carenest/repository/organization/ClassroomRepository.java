package com.carenest.repository.organization;

import com.carenest.entity.organization.Classroom;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    @Query("select c.campusId from Classroom c where c.id = :classroomId")
    Optional<Long> findCampusIdById(@Param("classroomId") Long classroomId);

    long countBySchoolYearId(Long schoolYearId);

    long countByCampusId(Long campusId);

    long countByAgeGroupId(Long ageGroupId);

    boolean existsByRoomId(Long roomId);

    @Query("select c.roomId as roomId, count(c) as total from Classroom c where c.roomId in :roomIds group by c.roomId")
    List<RoomUsage> countByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    interface RoomUsage {
        Long getRoomId();

        long getTotal();
    }

    List<Classroom> findAllByCampusIdAndSchoolYearIdOrderByNameAsc(Long campusId, Long schoolYearId);

    List<Classroom> findAllBySchoolYearIdOrderByCampusIdAscNameAsc(Long schoolYearId);

    @Query("select c.schoolYearId as schoolYearId, count(c) as classCount from Classroom c"
            + " where c.schoolYearId in :schoolYearIds group by c.schoolYearId")
    List<SchoolYearClassCount> countBySchoolYearIds(@Param("schoolYearIds") Collection<Long> schoolYearIds);

    // Lớp của các năm chưa kết thúc dùng phòng: chặn ngừng dùng/xóa phòng, hạ sức chứa
    @Query("select c from Classroom c, SchoolYear y where y.id = c.schoolYearId and c.roomId in :roomIds"
            + " and y.status <> com.carenest.utils.enums.SchoolYearStatus.CLOSED")
    List<Classroom> findOpenYearClassesByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    @Query("select c from Classroom c, SchoolYear y where y.id = c.schoolYearId and c.ageGroupId = :ageGroupId"
            + " and y.status <> com.carenest.utils.enums.SchoolYearStatus.CLOSED")
    List<Classroom> findOpenYearClassesByAgeGroupId(@Param("ageGroupId") Long ageGroupId);

    @Query("select count(c) > 0 from Classroom c where c.schoolYearId = :schoolYearId and c.campusId = :campusId"
            + " and lower(c.name) = lower(:name) and (:excludeId is null or c.id <> :excludeId)")
    boolean existsName(
            @Param("schoolYearId") Long schoolYearId,
            @Param("campusId") Long campusId,
            @Param("name") String name,
            @Param("excludeId") Long excludeId);

    @Query("select count(c) > 0 from Classroom c where c.schoolYearId = :schoolYearId and c.roomId = :roomId"
            + " and (:excludeId is null or c.id <> :excludeId)")
    boolean existsRoomInYear(
            @Param("schoolYearId") Long schoolYearId, @Param("roomId") Long roomId, @Param("excludeId") Long excludeId);

    @Modifying
    @Query("update Classroom c set c.status = com.carenest.utils.enums.ClassroomStatus.CLOSED"
            + " where c.schoolYearId = :schoolYearId")
    int closeAllBySchoolYearId(@Param("schoolYearId") Long schoolYearId);

    // Số trẻ đang học (enrollment còn hiệu lực) theo lớp; bảng enrollments thuộc module child, chỉ đọc
    @Query(
            value = "select e.classroom_id as classroomId, count(distinct e.child_id) as total from enrollments e"
                    + " where e.classroom_id in :classroomIds and e.valid_from <= :date"
                    + " and (e.valid_to is null or e.valid_to >= :date) group by e.classroom_id",
            nativeQuery = true)
    List<ClassroomCount> countActiveChildren(
            @Param("classroomIds") Collection<Long> classroomIds, @Param("date") LocalDate date);

    // Trẻ dự kiến vào lớp năm PLANNED theo bản xếp lớp (bảng class_placements thuộc module child, chỉ đọc)
    @Query(
            value = "select p.to_classroom_id as classroomId, count(*) as total from class_placements p"
                    + " where p.to_classroom_id in :classroomIds and p.action in ('PROMOTE', 'STAY')"
                    + " group by p.to_classroom_id",
            nativeQuery = true)
    List<ClassroomCount> countPlannedChildren(@Param("classroomIds") Collection<Long> classroomIds);

    @Query(value = "select count(*) from enrollments where classroom_id = :classroomId", nativeQuery = true)
    long countAllEnrollments(@Param("classroomId") Long classroomId);

    @Query(
            value = "select a.classroom_id as classroomId, count(distinct a.user_id) as total from staff_assignments a"
                    + " where a.staff_role = 'TEACHER' and a.classroom_id in :classroomIds"
                    + " and (a.valid_to is null or a.valid_to >= :date) group by a.classroom_id",
            nativeQuery = true)
    // GV hiện tại gồm cả phân công bắt đầu sau (năm học sắp tới)
    List<ClassroomCount> countActiveTeachers(
            @Param("classroomIds") Collection<Long> classroomIds, @Param("date") LocalDate date);

    interface SchoolYearClassCount {
        Long getSchoolYearId();

        long getClassCount();
    }

    interface ClassroomCount {
        Long getClassroomId();

        long getTotal();
    }

    @Query("select c.id from Classroom c where c.campusId in :campusIds")
    List<Long> findIdsByCampusIdIn(@Param("campusIds") Collection<Long> campusIds);

    // Lớp của năm học đang diễn ra tại date (lớp gắn năm học — năm sau là lớp khác)
    @Query(value = """
            select c.* from classrooms c join school_years y on y.id = c.school_year_id
            where c.campus_id = :campusId and :date between y.start_date and y.end_date
            order by c.name
            """, nativeQuery = true)
    List<Classroom> findCurrentByCampusId(@Param("campusId") Long campusId, @Param("date") LocalDate date);
}
