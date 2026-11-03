package com.carenest.service.meals.impl;

import com.carenest.service.meals.MealCountService;
import com.carenest.service.meals.StockLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * P-38 + ADR-0004: suất điểm trường–ngày đã chốt (sau commit) ⇒ trừ kho khô theo thực đơn tuần đã công bố. Lỗi ở đây
 * không làm hỏng xác nhận suất; xác nhận lại hoặc công bố lại sẽ đồng bộ lại (idempotent).
 */
@Component
@RequiredArgsConstructor
class StockDeductionListener {

    private final StockLedgerService stockLedgerService;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMealCountConfirmed(MealCountService.MealCountConfirmedEvent event) {
        stockLedgerService.syncDay(event.campusId(), event.date());
    }
}
