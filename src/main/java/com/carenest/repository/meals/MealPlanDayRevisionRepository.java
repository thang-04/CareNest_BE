package com.carenest.repository.meals;

import com.carenest.entity.meals.MealPlanDayRevision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanDayRevisionRepository extends JpaRepository<MealPlanDayRevision, Long> {

    List<MealPlanDayRevision> findByWeekIdOrderByChangedAtDescIdDesc(Long weekId);

    long countByWeekId(Long weekId);
}
