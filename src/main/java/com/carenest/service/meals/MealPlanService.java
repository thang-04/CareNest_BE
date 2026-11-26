package com.carenest.service.meals;

import com.carenest.dto.meals.MealPlanAmendRequest;
import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MealPlanRevisionResponse;
import com.carenest.dto.meals.MealPlanSummaryResponse;
import com.carenest.dto.meals.MenuStatisticsRequest;
import com.carenest.dto.meals.MenuStatisticsResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Kế hoạch thực đơn tuần theo khối, kiểm tự động, công bố cả tuần (NUT-08, NUT-13). */
public interface MealPlanService {

    List<MealPlanSummaryResponse> getMealPlans(CurrentUser currentUser, LocalDate from, LocalDate to);

    /** Tuần chưa lập ⇒ {@code status = NONE}, lưới 5 ngày trống. */
    MealPlanResponse getMealPlan(CurrentUser currentUser, LocalDate weekStart);

    MealPlanResponse saveMealPlan(CurrentUser currentUser, LocalDate weekStart, MealPlanRequest request);

    /** Thống kê định lượng, dinh dưỡng một ngày thực đơn cho N trẻ của một nhóm tuổi (NUT-07, NUT-24, NUT-25). */
    MenuStatisticsResponse getMenuStatistics(CurrentUser currentUser, MenuStatisticsRequest request);

    /** Chặn khi còn lỗi chặn; công bố xong đồng bộ trừ kho cho các ngày đã có suất chốt (P-38). */
    MealPlanResponse publishMealPlan(CurrentUser currentUser, LocalDate weekStart);

    /**
     * Sửa ngoại lệ một ngày của tuần đã công bố (NUT-08, P-39): ngày chưa tới hoặc hôm nay trước giờ chốt, lý do bắt
     * buộc, cân đối chỉ cảnh báo; lưu lịch sử, tính lại trừ kho ngày đã chốt suất, báo phụ huynh và bếp.
     */
    MealPlanResponse amendMealPlanDay(
            CurrentUser currentUser, LocalDate weekStart, LocalDate date, MealPlanAmendRequest request);

    /** Lịch sử sửa ngoại lệ của tuần, mới nhất trước; tuần chưa lập ⇒ rỗng. */
    List<MealPlanRevisionResponse> getMealPlanRevisions(CurrentUser currentUser, LocalDate weekStart);

    /** Thực đơn tuần vừa được công bố. */
    record MealPlanPublishedEvent(LocalDate weekStart, Long publishedBy) {}
}
