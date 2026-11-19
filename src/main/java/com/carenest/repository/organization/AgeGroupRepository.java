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

    boolean existsByCode(String code);

    Optional<AgeGroup> findFirstByNameIgnoreCase(String name);

    @Query("select count(g) > 0 from AgeGroup g where lower(g.name) = lower(:name)"
            + " and (:excludeId is null or g.id <> :excludeId)")
    boolean existsName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Query("select coalesce(max(g.sortOrder), 0) from AgeGroup g")
    short findMaxSortOrder();

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
            value = "insert into school_year_age_group_members (school_year_id, age_group_id)"
                    + " values (:yearId, :ageGroupId) on conflict do nothing",
            nativeQuery = true)
    void addToYear(@Param("yearId") Long yearId, @Param("ageGroupId") Long ageGroupId);

    @Modifying
    @Query(
            value = "insert into school_year_age_group_members (school_year_id, age_group_id)"
                    + " select y.id, :ageGroupId from school_years y where y.status <> 'CLOSED'"
                    + " on conflict do nothing",
            nativeQuery = true)
    void addToOpenYears(@Param("ageGroupId") Long ageGroupId);

    @Modifying
    @Query(
            value = "insert into school_year_age_group_members (school_year_id, age_group_id)"
                    + " select :targetYearId, age_group_id from school_year_age_group_members"
                    + " where school_year_id = :sourceYearId on conflict do nothing",
            nativeQuery = true)
    int copyYearAgeGroups(@Param("sourceYearId") Long sourceYearId, @Param("targetYearId") Long targetYearId);

    @Modifying
    @Query(
            value = "insert into school_year_age_group_members (school_year_id, age_group_id)"
                    + " select :yearId, g.id from age_groups g on conflict do nothing",
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

    interface LeaderUse {
        Long getUserId();

        Long getSchoolYearId();
    }
}
