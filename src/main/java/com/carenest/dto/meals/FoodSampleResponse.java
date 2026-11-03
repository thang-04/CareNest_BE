package com.carenest.dto.meals;

import com.carenest.utils.enums.FoodSampleDisposalStatus;
import com.carenest.utils.enums.FoodSampleStatus;
import com.carenest.utils.enums.MealSession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Bản lưu mẫu thức ăn (NUT-21). {@code disposableFrom} = lúc lấy mẫu + 24 giờ; {@code canApprove}: người xem duyệt được
 * lưu mẫu, hủy mẫu của điểm trường này (mã FOOD_SAFETY_APPROVE).
 */
public record FoodSampleResponse(
        Long id,
        Long campusId,
        LocalDate date,
        MealSession session,
        Instant sampledAt,
        List<SampleDish> dishes,
        String note,
        Long recordedBy,
        FoodSampleStatus status,
        Long approvedBy,
        Instant approvedAt,
        Instant disposableFrom,
        Instant disposedAt,
        Long disposalRecordedBy,
        FoodSampleDisposalStatus disposalStatus,
        Long disposalApprovedBy,
        Instant disposalApprovedAt,
        boolean canApprove) {

    public record SampleDish(Long id, String code, String name) {}
}
