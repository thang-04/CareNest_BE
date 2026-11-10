package com.carenest.repository.meals;

import com.carenest.entity.meals.StockLot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLotRepository extends JpaRepository<StockLot, Long> {

    /** Lô hết hạn trước dùng trước (NUT-11). */
    List<StockLot> findByCampusIdAndFoodIdOrderByExpiryDateAscIdAsc(Long campusId, Long foodId);

    List<StockLot> findByCampusIdOrderByExpiryDateAscIdAsc(Long campusId);

    List<StockLot> findByReceiptLineIdIn(Collection<Long> receiptLineIds);

    /**
     * Khóa dòng điểm trường tới hết transaction: mọi ghi kho của một điểm trường (xác nhận phiếu, trừ theo suất) chạy
     * tuần tự để tồn lô không âm.
     */
    @Query(value = "select id from campuses where id = :campusId for update", nativeQuery = true)
    Long lockCampus(@Param("campusId") Long campusId);
}
