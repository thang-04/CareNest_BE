package com.carenest.repository.meals;

import com.carenest.entity.meals.StockReceipt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {

    @EntityGraph(attributePaths = "lines")
    Optional<StockReceipt> findWithLinesById(Long id);

    // Phiếu của một điểm trường vài chục/tháng: lấy kèm dòng, lọc ngày/trạng thái ở service
    @EntityGraph(attributePaths = "lines")
    List<StockReceipt> findByCampusIdOrderByReceivedDateDescIdDesc(Long campusId);
}
