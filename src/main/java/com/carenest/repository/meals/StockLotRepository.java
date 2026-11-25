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

    /**
     * Khóa dòng điểm trường tới hết transaction: mọi ghi kho của một điểm trường (xác nhận phiếu, trừ theo suất) chạy
     * tuần tự để tồn lô không âm.
     */
    @Query(value = "select id from campuses where id = :campusId for update", nativeQuery = true)
    Long lockCampus(@Param("campusId") Long campusId);

    /**
     * Số lô đã vào kho của điểm trường (phiếu đã xác nhận, kể cả lô hết tồn) trong các thực phẩm và số lô cho trước; phiếu
     * đã hủy không tính để nhập lại đúng số lô. {@code lotNos} đã chuyển chữ thường, so khớp không phân biệt hoa thường.
     */
    @Query(
            value = "select l.food_id as foodId, l.lot_no as lotNo from stock_receipt_lines l "
                    + "join stock_receipts r on r.id = l.receipt_id "
                    + "where r.campus_id = :campusId and r.status = 'CONFIRMED' "
                    + "and l.food_id in (:foodIds) and lower(l.lot_no) in (:lotNos)",
            nativeQuery = true)
    List<LotKey> findConfirmedLotKeys(
            @Param("campusId") Long campusId,
            @Param("foodIds") Collection<Long> foodIds,
            @Param("lotNos") Collection<String> lotNos);

    interface LotKey {
        Long getFoodId();

        String getLotNo();
    }
}
