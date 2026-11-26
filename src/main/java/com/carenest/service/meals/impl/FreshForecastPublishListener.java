package com.carenest.service.meals.impl;

import com.carenest.service.meals.FreshGoodsService;
import com.carenest.service.meals.MealPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ADR-0004: công bố thực đơn tuần (sau commit) ⇒ tự lập bản dự trù từng điểm trường từ bản cân đối. Lỗi ở đây không hủy
 * việc công bố; người dùng lập bản dự trù ở màn Bản dự trù.
 */
@Component
@RequiredArgsConstructor
class FreshForecastPublishListener {

    private final FreshGoodsService freshGoodsService;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMealPlanPublished(MealPlanService.MealPlanPublishedEvent event) {
        freshGoodsService.createForecastsForPublishedWeek(event.weekStart(), event.publishedBy());
    }
}
