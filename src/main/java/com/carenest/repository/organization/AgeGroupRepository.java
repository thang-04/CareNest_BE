package com.carenest.repository.organization;

import com.carenest.entity.organization.AgeGroup;
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
}
