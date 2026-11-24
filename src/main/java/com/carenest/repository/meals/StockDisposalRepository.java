package com.carenest.repository.meals;

import com.carenest.entity.meals.StockDisposal;
import com.carenest.utils.enums.StockDisposalKind;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockDisposalRepository extends JpaRepository<StockDisposal, Long> {

    /** Số phiếu cùng loại đã lập trong ngày của điểm trường, để đánh số tiếp (gọi khi đang khóa điểm trường). */
    long countByCampusIdAndDisposalDateAndKind(Long campusId, LocalDate disposalDate, StockDisposalKind kind);
}
