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

    // Ngày nghỉ/ghi chú nằm trong cột jsonb của tuần; lấy kèm món một lần
    @EntityGraph(attributePaths = "items")
    Optional<MealPlanWeek> findByWeekStart(LocalDate weekStart);

    List<MealPlanWeek> findByWeekStartBetweenOrderByWeekStartAsc(LocalDate from, LocalDate to);

    // Kỳ dự trù hàng tươi ≤ 31 ngày (≤ 6 tuần): lấy kèm món một lần
    @EntityGraph(attributePaths = "items")
    List<MealPlanWeek> findWithDaysByWeekStartBetweenOrderByWeekStartAsc(LocalDate from, LocalDate to);

    // Tuần chưa qua có chứa món: hộp xác nhận ngừng dùng món liệt kê nơi đang dùng
    @Query("select distinct w from MealPlanWeek w join w.items i"
            + " where i.dishId = :dishId and w.weekStart >= :from order by w.weekStart")
    List<MealPlanWeek> findFromWeekUsingDish(@Param("dishId") Long dishId, @Param("from") LocalDate from);

    /**
     * Khóa dòng tuần tới hết transaction: lưu lưới và công bố chạy tuần tự, công bố luôn kiểm đúng lưới nó công bố
     * (NUT-08). Ngày, món của tuần nạp sau khi đã giữ khóa nên là bản mới nhất.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from MealPlanWeek w where w.weekStart = :weekStart")
    Optional<MealPlanWeek> findForUpdateByWeekStart(@Param("weekStart") LocalDate weekStart);
}
