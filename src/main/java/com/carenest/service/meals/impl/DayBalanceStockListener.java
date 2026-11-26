package com.carenest.service.meals.impl;

import com.carenest.entity.meals.MealCount;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.service.meals.DayBalanceService;
import com.carenest.service.meals.StockLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ADR-0004: lưu bản cân đối (sau commit) ⇒ điểm trường đã chốt suất ngày đó được trừ lại kho khô theo gam mới. Lỗi ở
 * đây không hủy bản cân đối; hub Bán trú hôm nay báo "chưa trừ kho" và người dùng đồng bộ lại.
 */
@Component
@RequiredArgsConstructor
class DayBalanceStockListener {

    private final MealCountRepository mealCountRepository;
    private final StockLedgerService stockLedgerService;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDayBalanceSaved(DayBalanceService.DayBalanceSavedEvent event) {
        mealCountRepository.findByDateBetween(event.date(), event.date()).stream()
                .filter(MealCount::isConfirmed)
                .map(MealCount::getCampusId)
                .distinct()
                .forEach(campusId -> stockLedgerService.syncDay(campusId, event.date()));
    }
}
