package com.carenest.repository.meals;

import com.carenest.entity.meals.StockReceipt;
import com.carenest.utils.enums.StockReceiptStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {

    @EntityGraph(attributePaths = "lines")
    Optional<StockReceipt> findWithLinesById(Long id);

    // Phiếu của một điểm trường vài chục/tháng: lấy kèm dòng, lọc ngày/trạng thái ở service
    @EntityGraph(attributePaths = "lines")
    List<StockReceipt> findByCampusIdOrderByReceivedDateDescIdDesc(Long campusId);

    @EntityGraph(attributePaths = "lines")
    List<StockReceipt> findByCampusIdAndReceivedDateAndFromIntakeTrueOrderByIdAsc(
            Long campusId, LocalDate receivedDate);

    @Query("select l.foodId as foodId, sum(l.quantity) as quantity from StockReceiptLine l"
            + " where l.receipt.forecastId = :forecastId and l.receipt.status = :status group by l.foodId")
    List<FoodQuantity> sumLinesByForecast(
            @Param("forecastId") Long forecastId, @Param("status") StockReceiptStatus status);

    interface FoodQuantity {
        Long getFoodId();

        BigDecimal getQuantity();
    }
}
