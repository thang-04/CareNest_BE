package com.carenest.repository.meals;

import com.carenest.entity.meals.MealCount;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealCountRepository extends JpaRepository<MealCount, Long> {

    @EntityGraph(attributePaths = "lines")
    List<MealCount> findByCampusIdInAndDate(List<Long> campusIds, LocalDate date);

    @EntityGraph(attributePaths = "lines")
    List<MealCount> findByCampusIdAndDate(Long campusId, LocalDate date);
}
