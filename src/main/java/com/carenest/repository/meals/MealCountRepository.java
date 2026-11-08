package com.carenest.repository.meals;

import com.carenest.entity.meals.MealCount;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MealCountRepository extends JpaRepository<MealCount, Long> {

    interface Scope {
        Long getCampusId();

        LocalDate getDate();
    }

    @Query("SELECT c.campusId AS campusId, c.date AS date FROM MealCount c WHERE c.id = :id")
    Optional<Scope> findScopeById(Long id);

    // Khóa sổ chung trước khi đọc entity, kể cả khi sổ chưa được tạo; tự nhả khi kết thúc transaction.
    @Query(
            value =
                    "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(concat('carenest:meal:', CAST(:campusId AS text), ':', CAST(:date AS text)), 0))",
            nativeQuery = true)
    Integer lockCampusDay(Long campusId, LocalDate date);

    @EntityGraph(attributePaths = "lines")
    List<MealCount> findByCampusIdInAndDate(List<Long> campusIds, LocalDate date);

    @EntityGraph(attributePaths = "lines")
    List<MealCount> findByCampusIdAndDate(Long campusId, LocalDate date);

    List<MealCount> findByDateBetween(LocalDate from, LocalDate to);
}
