package com.carenest.dto.meals;

import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Một lần sửa ngoại lệ ngày của thực đơn đã công bố: trước/sau, lý do, cảnh báo cân đối lúc sửa, người sửa. */
public record MealPlanRevisionResponse(
        Long id,
        LocalDate menuDate,
        RevisionDay before,
        RevisionDay after,
        String reason,
        List<RevisionIssue> issues,
        Long changedBy,
        String changedByName,
        Instant changedAt) {

    public record RevisionDay(String note, List<RevisionItem> items) {}

    public record RevisionItem(MealSession session, MenuBlock block, Long dishId, String dishName) {}

    public record RevisionIssue(String severity, String code, String message) {}
}
