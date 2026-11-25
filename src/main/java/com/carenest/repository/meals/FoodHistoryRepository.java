package com.carenest.repository.meals;

import com.carenest.entity.meals.FoodHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodHistoryRepository extends JpaRepository<FoodHistory, Long> {

    List<FoodHistory> findByFoodIdOrderByChangedAtAscIdAsc(Long foodId);
}
