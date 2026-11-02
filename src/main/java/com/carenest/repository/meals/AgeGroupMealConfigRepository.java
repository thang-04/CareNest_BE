package com.carenest.repository.meals;

import com.carenest.entity.meals.AgeGroupMealConfig;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgeGroupMealConfigRepository extends JpaRepository<AgeGroupMealConfig, Long> {

    List<AgeGroupMealConfig> findByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate date);

    List<AgeGroupMealConfig> findByAgeGroupIdOrderByEffectiveFromDesc(Long ageGroupId);

    boolean existsByAgeGroupId(Long ageGroupId);

    boolean existsByAgeGroupIdAndEffectiveFrom(Long ageGroupId, LocalDate effectiveFrom);

    boolean existsByAgeGroupIdAndEffectiveFromAndIdNot(Long ageGroupId, LocalDate effectiveFrom, Long id);
}
