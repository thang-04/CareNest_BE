package com.carenest.dto.meals;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * HT duyệt, từ chối hoặc thu hồi yêu cầu (INS-05). {@code note} bắt buộc khi từ chối hoặc thu hồi (kiểm ở service);
 * {@code validHours} chỉ dùng khi duyệt, trống ⇒ hạn mặc định.
 */
public record InspectionApprovalDecisionRequest(
        @Size(max = 500) String note, @Min(1) @Max(168) Integer validHours) {}
