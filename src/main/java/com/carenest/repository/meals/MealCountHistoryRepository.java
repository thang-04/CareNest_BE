package com.carenest.repository.meals;

import com.carenest.entity.meals.MealCountHistory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealCountHistoryRepository extends JpaRepository<MealCountHistory, Long> {

    List<MealCountHistory> findByMealCountIdInOrderByAtAscIdAsc(Collection<Long> mealCountIds);
}
