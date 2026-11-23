package com.carenest.repository.meals;

import com.carenest.entity.meals.AgeGroupMealConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgeGroupMealConfigRepository extends JpaRepository<AgeGroupMealConfig, Long> {

    @org.springframework.data.jpa.repository.Query(
            value = "select age_group_id from nutrition_group_settings where enabled = false",
            nativeQuery = true)
    java.util.Set<Long> findDisabledNutritionGroupIds();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value =
                    "insert into nutrition_group_settings (age_group_id, enabled, updated_by) values (:id, :enabled, :userId) on conflict (age_group_id) do update set enabled = excluded.enabled, updated_by = excluded.updated_by, updated_at = now()",
            nativeQuery = true)
    void setNutritionGroupEnabled(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("enabled") boolean enabled,
            @org.springframework.data.repository.query.Param("userId") Long userId);

    List<AgeGroupMealConfig> findByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate date);

    List<AgeGroupMealConfig> findByAgeGroupIdOrderByEffectiveFromDesc(Long ageGroupId);

    boolean existsByAgeGroupId(Long ageGroupId);

    Optional<AgeGroupMealConfig> findByAgeGroupIdAndEffectiveFrom(Long ageGroupId, LocalDate effectiveFrom);

    boolean existsByAgeGroupIdAndEffectiveFrom(Long ageGroupId, LocalDate effectiveFrom);

    boolean existsByAgeGroupIdAndEffectiveFromAndIdNot(Long ageGroupId, LocalDate effectiveFrom, Long id);
}
