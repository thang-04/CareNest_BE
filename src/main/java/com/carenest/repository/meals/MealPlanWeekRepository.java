package com.carenest.repository.meals;

import com.carenest.entity.meals.MealPlanWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanWeekRepository extends JpaRepository<MealPlanWeek, Long> {

    // 5 ngày/tuần: lấy kèm ngày, món của ngày nạp lười trong transaction (không fetch 2 bag cùng lúc)
    @EntityGraph(attributePaths = "days")
    Optional<MealPlanWeek> findByWeekStart(LocalDate weekStart);

    List<MealPlanWeek> findByWeekStartBetweenOrderByWeekStartAsc(LocalDate from, LocalDate to);
}
