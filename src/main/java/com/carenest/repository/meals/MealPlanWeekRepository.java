package com.carenest.repository.meals;

import com.carenest.entity.meals.MealPlanWeek;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealPlanWeekRepository extends JpaRepository<MealPlanWeek, Long> {

    // 5 ngày/tuần: lấy kèm ngày, món của ngày nạp lười trong transaction (không fetch 2 bag cùng lúc)
    @EntityGraph(attributePaths = "days")
    Optional<MealPlanWeek> findByWeekStart(LocalDate weekStart);

    List<MealPlanWeek> findByWeekStartBetweenOrderByWeekStartAsc(LocalDate from, LocalDate to);

    // Kỳ dự trù hàng tươi ≤ 31 ngày (≤ 6 tuần): lấy kèm ngày một lần, món của ngày nạp lười trong transaction
    @EntityGraph(attributePaths = "days")
    List<MealPlanWeek> findWithDaysByWeekStartBetweenOrderByWeekStartAsc(LocalDate from, LocalDate to);

    /**
     * Khóa dòng tuần tới hết transaction: lưu lưới và công bố chạy tuần tự, công bố luôn kiểm đúng lưới nó công bố
     * (NUT-08). Ngày, món của tuần nạp sau khi đã giữ khóa nên là bản mới nhất.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from MealPlanWeek w where w.weekStart = :weekStart")
    Optional<MealPlanWeek> findForUpdateByWeekStart(@Param("weekStart") LocalDate weekStart);
}
