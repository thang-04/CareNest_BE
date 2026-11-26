package com.carenest.service.attendance;

import com.carenest.utils.enums.MealSession;
import java.time.LocalDate;
import java.util.Set;

/**
 * Chặn đổi suất của bữa đã khóa (ATT-02a, ATT-07): sổ suất đã xác nhận hoặc phiếu bếp đã ký. Module suất ăn cài đặt
 * interface này để điểm danh không phụ thuộc repository của module khác.
 */
public interface MealChangeGuard {

    /**
     * Khóa cơ sở–ngày rồi kiểm tra; ném {@code MEAL_COUNT_INSPECTION_SIGNED} hoặc {@code MEAL_COUNT_ALREADY_CONFIRMED}
     * nếu bất kỳ bữa nào trong {@code sessions} đã khóa. Tập rỗng thì bỏ qua.
     */
    void ensureChangeAllowed(Long campusId, Long schoolYearId, LocalDate date, Set<MealSession> sessions);
}
