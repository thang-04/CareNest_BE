package com.carenest.service.meals;

import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MealPlanSummaryResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Kế hoạch thực đơn tuần theo khối, kiểm tự động, công bố cả tuần (NUT-08, NUT-13). */
public interface MealPlanService {

    List<MealPlanSummaryResponse> getMealPlans(CurrentUser currentUser, LocalDate from, LocalDate to);

    /** Tuần chưa lập ⇒ {@code status = NONE}, lưới 5 ngày trống. */
    MealPlanResponse getMealPlan(CurrentUser currentUser, LocalDate weekStart);

    MealPlanResponse saveMealPlan(CurrentUser currentUser, LocalDate weekStart, MealPlanRequest request);

    /** Chặn khi còn lỗi chặn; công bố xong đồng bộ trừ kho cho các ngày đã có suất chốt (P-38). */
    MealPlanResponse publishMealPlan(CurrentUser currentUser, LocalDate weekStart);
}
