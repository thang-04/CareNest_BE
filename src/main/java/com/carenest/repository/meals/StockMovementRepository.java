package com.carenest.repository.meals;

import com.carenest.entity.meals.StockMovement;
import com.carenest.utils.enums.StockMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** Tồn lô = tổng bút toán của lô. */
    @Query("select m.lotId as lotId, sum(m.quantity) as balance from StockMovement m"
            + " where m.lotId in :lotIds group by m.lotId")
    List<LotBalance> sumByLotIds(@Param("lotIds") Collection<Long> lotIds);

    /** Bút toán theo suất của một ngày ăn, mới nhất trước (trả lại lô khi suất giảm). */
    List<StockMovement> findByCampusIdAndMealDateAndFoodIdOrderByIdDesc(Long campusId, LocalDate mealDate, Long foodId);

    @Query("select m from StockMovement m where m.campusId = :campusId"
            + " and m.createdAt >= :from and m.createdAt < :to"
            + " and (:foodId is null or m.foodId = :foodId)"
            + " and (:type is null or m.movementType = :type)"
            + " order by m.id desc")
    Page<StockMovement> search(
            @Param("campusId") Long campusId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("foodId") Long foodId,
            @Param("type") StockMovementType type,
            Pageable pageable);

    interface LotBalance {
        Long getLotId();

        BigDecimal getBalance();
    }
}
