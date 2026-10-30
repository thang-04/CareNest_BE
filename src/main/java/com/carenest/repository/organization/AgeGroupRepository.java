package com.carenest.repository.organization;

import com.carenest.entity.organization.AgeGroup;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgeGroupRepository extends JpaRepository<AgeGroup, Long> {

    List<AgeGroup> findAllByOrderBySortOrderAsc();

    boolean existsByCode(String code);

    @Query("select count(g) > 0 from AgeGroup g where lower(g.name) = lower(:name)"
            + " and (:excludeId is null or g.id <> :excludeId)")
    boolean existsName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Query("select coalesce(max(g.sortOrder), 0) from AgeGroup g")
    short findMaxSortOrder();

    @Query(value = "select count(*) from staff_assignments where age_group_id = :ageGroupId", nativeQuery = true)
    long countStaffAssignments(@Param("ageGroupId") Long ageGroupId);

    @Query(
            value = "select a.age_group_id as ageGroupId, u.id as userId, u.full_name as fullName"
                    + " from staff_assignments a join user_accounts u on u.id = a.user_id"
                    + " where a.staff_role = 'AGE_GROUP_TEAM_LEADER' and a.valid_from <= :date"
                    + " and (a.valid_to is null or a.valid_to >= :date)",
            nativeQuery = true)
    List<TeamLeaderRow> findActiveTeamLeaders(@Param("date") LocalDate date);

    interface TeamLeaderRow {
        Long getAgeGroupId();

        Long getUserId();

        String getFullName();
    }
}
