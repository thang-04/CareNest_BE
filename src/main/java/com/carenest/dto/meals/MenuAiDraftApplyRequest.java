package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Dùng nháp gợi ý thực đơn. {@code days}: lưới người duyệt đã sửa trên nháp tuần (bỏ trống = dùng nguyên nháp).
 * {@code reason}: bắt buộc khi thay món trên tuần đã công bố (P-39).
 */
public record MenuAiDraftApplyRequest(
        @Valid List<MenuAiDraftGridRequest.Day> days,
        @Size(max = 500) String reason) {}
