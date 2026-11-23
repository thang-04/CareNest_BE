package com.carenest.dto.meals;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu mở ghi mẫu ngày cũ (INS-05). PHT: gửi chờ HT duyệt; HT: tự mở ngay. {@code validHours} chỉ HT dùng (tự mở):
 * trống ⇒ hạn mặc định. Lý do kiểm độ dài ở service để trả mã lỗi riêng.
 */
public record InspectionApprovalRequest(
        @Size(max = 500) String reason, @Min(1) @Max(168) Integer validHours) {}
