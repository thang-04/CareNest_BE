package com.carenest.repository.organization;

import com.carenest.entity.organization.AgeGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgeGroupRepository extends JpaRepository<AgeGroup, Long> {

    List<AgeGroup> findAllByOrderBySortOrderAsc();

    List<AgeGroup> findAllByStandardGroupTrueOrderBySortOrderAsc();

    Optional<AgeGroup> findFirstByNameIgnoreCase(String name);

    @Query(value = "select count(*) from staff_assignments where age_group_id = :ageGroupId", nativeQuery = true)
    long countStaffAssignments(@Param("ageGroupId") Long ageGroupId);

    // Nhóm tuổi dùng chung mọi năm: lỗi xóa nêu năm học và tổ trưởng đang giữ nhóm (mới nhất)
    @Query(
            value = "select user_id as userId, school_year_id as schoolYearId from staff_assignments"
                    + " where age_group_id = :ageGroupId order by valid_from desc, id desc limit 1",
            nativeQuery = true)
    Optional<LeaderUse> findLatestLeader(@Param("ageGroupId") Long ageGroupId);

    @Query(
            value = "select school_year_id from classrooms where age_group_id = :ageGroupId"
                    + " order by school_year_id desc limit 1",
            nativeQuery = true)
    Optional<Long> findLatestClassYearId(@Param("ageGroupId") Long ageGroupId);

    // Nhóm tuổi của từng năm học (V148): danh mục chung giữ mã, mỗi năm có danh sách riêng
    @Query(
            value = "select age_group_id from school_year_age_group_members where school_year_id = :yearId",
            nativeQuery = true)
    List<Long> findYearAgeGroupIds(@Param("yearId") Long yearId);

    @Query(
            value = "select count(*) > 0 from school_year_age_group_members"
                    + " where school_year_id = :yearId and age_group_id = :ageGroupId",
            nativeQuery = true)
    boolean isInYear(@Param("yearId") Long yearId, @Param("ageGroupId") Long ageGroupId);

    @Modifying
    @Query(
            value =
                    "insert into school_year_age_group_members (school_year_id, age_group_id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note)"
                            + " select :yearId, g.id, g.name, g.short_name, g.age_from_months, g.age_to_months, g.meal_sessions, g.max_class_size, g.nutrition_note"
                            + " from age_groups g where g.id = :ageGroupId on conflict do nothing",
            nativeQuery = true)
    void addToYear(@Param("yearId") Long yearId, @Param("ageGroupId") Long ageGroupId);

    @Modifying
    @Query(
            value =
                    "insert into school_year_age_group_members (school_year_id, age_group_id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note)"
                            + " select :targetYearId, m.age_group_id, coalesce(m.name, g.name), coalesce(m.short_name, g.short_name),"
                            + " coalesce(m.age_from_months, g.age_from_months), coalesce(m.age_to_months, g.age_to_months),"
                            + " coalesce(m.meal_sessions, g.meal_sessions), coalesce(m.max_class_size, g.max_class_size),"
                            + " coalesce(m.nutrition_note, g.nutrition_note)"
                            + " from school_year_age_group_members m join age_groups g on g.id = m.age_group_id"
                            + " where m.school_year_id = :sourceYearId on conflict do nothing",
            nativeQuery = true)
    int copyYearAgeGroups(@Param("sourceYearId") Long sourceYearId, @Param("targetYearId") Long targetYearId);

    @Modifying
    @Query(
            value =
                    "insert into school_year_age_group_members (school_year_id, age_group_id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note)"
                            + " select :yearId, g.id, g.name, g.short_name, g.age_from_months, g.age_to_months, g.meal_sessions, g.max_class_size, g.nutrition_note from age_groups g on conflict do nothing",
            nativeQuery = true)
    void addAllToYear(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value = "delete from school_year_age_group_members"
                    + " where school_year_id = :yearId and age_group_id = :ageGroupId",
            nativeQuery = true)
    void removeFromYear(@Param("yearId") Long yearId, @Param("ageGroupId") Long ageGroupId);

    @Query(
            value = "select count(*) from school_year_age_group_members where age_group_id = :ageGroupId",
            nativeQuery = true)
    long countYears(@Param("ageGroupId") Long ageGroupId);

    @Query(
            value = "select count(*) from classrooms where age_group_id = :ageGroupId and school_year_id = :yearId",
            nativeQuery = true)
    long countClassesInYear(@Param("ageGroupId") Long ageGroupId, @Param("yearId") Long yearId);

    @Query(
            value = "select user_id from staff_assignments where age_group_id = :ageGroupId"
                    + " and school_year_id = :yearId order by valid_from desc, id desc limit 1",
            nativeQuery = true)
    Optional<Long> findLeaderInYear(@Param("ageGroupId") Long ageGroupId, @Param("yearId") Long yearId);

    /** Thông tin nhóm tuổi riêng của năm (V154 bảo đảm các cột bắt buộc không null). */
    @Query(
            value =
                    "select age_group_id as ageGroupId, name, short_name as shortName, age_from_months as ageFromMonths, age_to_months as ageToMonths, meal_sessions as mealSessions, max_class_size as maxClassSize, nutrition_note as nutritionNote"
                            + " from school_year_age_group_members where school_year_id = :yearId",
            nativeQuery = true)
    List<YearAgeGroupInfo> findYearAgeGroupInfo(@Param("yearId") Long yearId);

    @Modifying
    @Query(
            value = "update school_year_age_group_members set name = :name, short_name = :shortName,"
                    + " age_from_months = :ageFrom, age_to_months = :ageTo, meal_sessions = :mealSessions,"
                    + " max_class_size = :maxClassSize, nutrition_note = :nutritionNote"
                    + " where school_year_id = :yearId and age_group_id = :ageGroupId",
            nativeQuery = true)
    void updateYearInfo(
            @Param("yearId") Long yearId,
            @Param("ageGroupId") Long ageGroupId,
            @Param("name") String name,
            @Param("shortName") String shortName,
            @Param("ageFrom") short ageFrom,
            @Param("ageTo") short ageTo,
            @Param("mealSessions") String mealSessions,
            @Param("maxClassSize") short maxClassSize,
            @Param("nutritionNote") String nutritionNote);

    /** Kích hoạt năm: bảng chung nhận thông tin nhóm tuổi của năm mới (module khác đọc bảng chung). */
    @Modifying
    @Query(
            value = "update age_groups g set name = m.name, short_name = m.short_name,"
                    + " age_from_months = m.age_from_months, age_to_months = m.age_to_months,"
                    + " meal_sessions = m.meal_sessions, max_class_size = m.max_class_size,"
                    + " nutrition_note = m.nutrition_note, updated_at = now()"
                    + " from school_year_age_group_members m"
                    + " where m.age_group_id = g.id and m.school_year_id = :yearId and m.name is not null",
            nativeQuery = true)
    int applyYearInfo(@Param("yearId") Long yearId);

    interface YearAgeGroupInfo {
        Long getAgeGroupId();

        String getName();

        String getShortName();

        Short getAgeFromMonths();

        Short getAgeToMonths();

        String getMealSessions();

        Short getMaxClassSize();

        String getNutritionNote();
    }

    interface LeaderUse {
        Long getUserId();

        Long getSchoolYearId();
    }
}
