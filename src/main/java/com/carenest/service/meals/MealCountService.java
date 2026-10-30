package com.carenest.service.meals;

import com.carenest.dto.meals.MealAdjustRequest;
import com.carenest.dto.meals.MealCountDayResponse;
import com.carenest.dto.meals.MealCountResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Sổ báo ăn (số suất) theo điểm trường/ngày/bữa: tổng hợp từ điểm danh, xác nhận, điều chỉnh (NUT-01..03, ATT-07). */
public interface MealCountService {

    /** schoolYearId = năm chọn ở header; null = năm đang hoạt động. Sau giờ chốt tự tổng hợp số của ngày. */
    MealCountDayResponse getMealCounts(CurrentUser currentUser, LocalDate date, Long schoolYearId);

    MealCountResponse confirm(CurrentUser currentUser, Long mealCountId);

    List<MealCountResponse> adjust(CurrentUser currentUser, MealAdjustRequest request);

    /** Điểm danh của lớp đổi sau khi đã tổng hợp ⇒ số đã xác nhận chuyển chờ xác nhận lại (ATT-07, ADR-0005). */
    void markAttendanceChanged(LocalDate date, Long campusId, Long classroomId);
}
