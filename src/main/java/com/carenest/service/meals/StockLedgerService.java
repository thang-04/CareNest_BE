package com.carenest.service.meals;

import com.carenest.dto.common.PageResponse;
import com.carenest.dto.meals.StockLotResponse;
import com.carenest.dto.meals.StockMovementResponse;
import com.carenest.dto.meals.StockShortfallResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.StockMovementType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Sổ kho khô: tồn theo lô, lịch sử nhập/trừ, phần thiếu; trừ kho theo suất đã xác nhận (NUT-11, P-38). */
public interface StockLedgerService {

    List<StockLotResponse> getLots(CurrentUser currentUser, Long campusId, Long foodId, boolean includeEmpty);

    PageResponse<StockMovementResponse> getMovements(
            CurrentUser currentUser,
            Long campusId,
            LocalDate from,
            LocalDate to,
            Long foodId,
            StockMovementType type,
            int page,
            int size);

    List<StockShortfallResponse> getShortfalls(CurrentUser currentUser, Long campusId);

    /**
     * Đưa kho khô của điểm trường–ngày về đúng nhu cầu theo thực đơn tuần đã công bố × suất đã chốt: chỉ ghi chênh
     * lệch so với lần trước, gọi lại không ghi thêm.
     */
    void syncDay(Long campusId, LocalDate date);

    /** Trừ phần thiếu của các ngày trước (cũ trước) bằng lô mới nhập của các thực phẩm này. */
    void settleShortfalls(Long campusId, Collection<Long> foodIds, Long receiptId, Long userId);
}
