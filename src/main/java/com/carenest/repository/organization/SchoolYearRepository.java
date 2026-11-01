package com.carenest.repository.organization;

import com.carenest.entity.organization.SchoolYear;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolYearRepository extends JpaRepository<SchoolYear, Long> {

    List<SchoolYear> findAllByOrderByStartDateDesc();

    Optional<SchoolYear> findFirstByStatus(SchoolYearStatus status);

    Optional<SchoolYear> findFirstByStatusOrderByStartDateAsc(SchoolYearStatus status);

    @Query("select y from SchoolYear y where y.startDate <= :date and y.endDate >= :date")
    Optional<SchoolYear> findContaining(@Param("date") LocalDate date);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    // Hai khoảng [start, end] giao nhau khi start1 <= end2 và start2 <= end1; excludeId = null khi tạo mới
    @Query("select y from SchoolYear y where y.startDate <= :endDate and y.endDate >= :startDate"
            + " and (:excludeId is null or y.id <> :excludeId) order by y.startDate")
    List<SchoolYear> findOverlapping(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId);

    // Xóa năm chưa bắt đầu: phân công (theo lớp của năm hoặc gắn năm), xếp lớp, lớp
    @Modifying
    @Query(
            value = "delete from staff_assignments where school_year_id = :yearId"
                    + " or classroom_id in (select id from classrooms where school_year_id = :yearId)",
            nativeQuery = true)
    void deletePlannedYearAssignments(@Param("yearId") Long yearId);

    @Modifying
    @Query(value = "delete from class_placements where school_year_id = :yearId", nativeQuery = true)
    void deletePlannedYearPlacements(@Param("yearId") Long yearId);

    @Modifying
    @Query(value = "delete from classrooms where school_year_id = :yearId", nativeQuery = true)
    void deletePlannedYearClasses(@Param("yearId") Long yearId);

    // CFG-YR-02: bản chụp phòng / điểm trường / nhóm tuổi lúc năm học kết thúc (bảng school_year_*)
    @Modifying
    @Query(value = "delete from school_year_rooms where school_year_id = :yearId", nativeQuery = true)
    void deleteRoomSnapshot(@Param("yearId") Long yearId);

    @Modifying
    @Query(value = "delete from school_year_campuses where school_year_id = :yearId", nativeQuery = true)
    void deleteCampusSnapshot(@Param("yearId") Long yearId);

    @Modifying
    @Query(value = "delete from school_year_age_groups where school_year_id = :yearId", nativeQuery = true)
    void deleteAgeGroupSnapshot(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value =
                    "insert into school_year_rooms (school_year_id, room_id, campus_id, code, name, room_type, capacity,"
                            + " manager_user_id, status, note) select :yearId, r.id, r.campus_id, r.code, r.name, r.room_type,"
                            + " r.capacity, r.manager_user_id, r.status, r.note from rooms r where r.status <> 'DELETED'",
            nativeQuery = true)
    void snapshotRooms(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value =
                    "insert into school_year_campuses (school_year_id, campus_id, code, name, short_name, address, phone,"
                            + " is_main) select :yearId, c.id, c.code, c.name, c.short_name, c.address, c.phone, c.is_main"
                            + " from campuses c",
            nativeQuery = true)
    void snapshotCampuses(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value =
                    "insert into school_year_age_groups (school_year_id, age_group_id, code, name, short_name, sort_order,"
                            + " age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note)"
                            + " select :yearId, g.id, g.code, g.name, g.short_name, g.sort_order, g.age_from_months,"
                            + " g.age_to_months, g.meal_sessions, g.max_class_size, g.nutrition_note from age_groups g",
            nativeQuery = true)
    void snapshotAgeGroups(@Param("yearId") Long yearId);

    @Query(
            value = "select room_id as roomId, campus_id as campusId, code, name, room_type as roomType, capacity,"
                    + " manager_user_id as managerUserId, status, note from school_year_rooms"
                    + " where school_year_id = :yearId order by campus_id, code",
            nativeQuery = true)
    List<RoomSnapshot> findRoomSnapshot(@Param("yearId") Long yearId);

    @Query(
            value = "select campus_id as campusId, code, name, short_name as shortName, address, phone,"
                    + " is_main as main from school_year_campuses where school_year_id = :yearId order by campus_id",
            nativeQuery = true)
    List<CampusSnapshot> findCampusSnapshot(@Param("yearId") Long yearId);

    @Query(
            value = "select age_group_id as ageGroupId, code, name, short_name as shortName, sort_order as sortOrder,"
                    + " age_from_months as ageFromMonths, age_to_months as ageToMonths, meal_sessions as mealSessions,"
                    + " max_class_size as maxClassSize, nutrition_note as nutritionNote from school_year_age_groups"
                    + " where school_year_id = :yearId order by sort_order",
            nativeQuery = true)
    List<AgeGroupSnapshot> findAgeGroupSnapshot(@Param("yearId") Long yearId);

    interface RoomSnapshot {
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

    interface CampusSnapshot {
        Long getCampusId();

        String getCode();

        String getName();

        String getShortName();

        String getAddress();

        String getPhone();

        Boolean getMain();
    }

    interface AgeGroupSnapshot {
        Long getAgeGroupId();

        String getCode();

        String getName();

        String getShortName();

        Short getSortOrder();

        Short getAgeFromMonths();

        Short getAgeToMonths();

        String getMealSessions();

        Short getMaxClassSize();

        String getNutritionNote();
    }
}
