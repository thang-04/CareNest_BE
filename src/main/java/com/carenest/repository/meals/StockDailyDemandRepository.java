package com.carenest.repository.meals;

import com.carenest.entity.meals.StockDailyDemand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockDailyDemandRepository extends JpaRepository<StockDailyDemand, Long> {

    List<StockDailyDemand> findByCampusIdAndMealDate(Long campusId, LocalDate mealDate);

    /** Phần thiếu của một thực phẩm, ngày ăn cũ trước — trừ tiếp khi có phiếu nhập bổ sung. */
    List<StockDailyDemand> findByCampusIdAndFoodIdAndShortfallQtyGreaterThanOrderByMealDateAscIdAsc(
            Long campusId, Long foodId, BigDecimal zero);

    List<StockDailyDemand> findByCampusIdAndShortfallQtyGreaterThanOrderByMealDateAscFoodIdAsc(
            Long campusId, BigDecimal zero);
}
