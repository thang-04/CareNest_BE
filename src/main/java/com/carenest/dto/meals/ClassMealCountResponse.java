package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Suất ăn một lớp trong ngày cho giáo viên (NUT-02, ATT-02): mỗi bữa số suất thường, suất thay thế và trạng thái sổ của
 * điểm trường. Không có tên trẻ. status OPEN = tạm tính trước giờ chốt (sổ chưa lập).
 */
public record ClassMealCountResponse(
        Long classId,
        String className,
        LocalDate date,
        LocalDate today,
        String cutoff,
        boolean schoolDay,
        boolean locked,
        List<Meal> meals) {

    public record Meal(
            MealSession session,
            int normal,
            int substitute,
            String status,
            boolean needsReconfirmation,
            Instant confirmedAt) {}
}
