package com.carenest.service.meals;

import com.carenest.dto.meals.ClassMealCountResponse;
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

    /**
     * Suất ăn của một lớp trong ngày cho người xem được lớp (giáo viên được phân công, HT, PHT theo phạm vi); lớp
     * không xem được ⇒ CLASS_ACCESS_DENIED. Chỉ đọc: không lập sổ, số lấy từ sổ đã lập hoặc tạm tính từ điểm danh.
     */
    ClassMealCountResponse getClassMealCount(CurrentUser currentUser, Long classId, LocalDate date);

    MealCountResponse confirm(CurrentUser currentUser, Long mealCountId);

    /**
     * seenServings / seenSubstitute: tổng suất và suất thay thế cơ sở–ngày người duyệt đang thấy (null = không kiểm);
     * số đã đổi ⇒ không xác nhận.
     */
    MealCountResponse confirm(CurrentUser currentUser, Long mealCountId, Integer seenServings, Integer seenSubstitute);

    /** Lập sổ báo ăn của hôm nay cho mọi cơ sở nếu chưa ai mở sổ sau giờ chốt (NUT-01): tác vụ cuối ngày. */
    void aggregateLockedDay();

    List<MealCountResponse> adjust(CurrentUser currentUser, MealAdjustRequest request);

    /** Điểm danh của lớp đổi sau khi đã tổng hợp ⇒ số đã xác nhận chuyển chờ xác nhận lại (ATT-07, ADR-0005). */
    void markAttendanceChanged(LocalDate date, Long campusId, Long classroomId);

    /** Số suất của điểm trường–ngày vừa chuyển sang đã chốt (xác nhận hoặc xác nhận lại); phát sau commit (ADR-0004). */
    record MealCountConfirmedEvent(Long campusId, LocalDate date) {}
}
