package com.carenest.dto.meals;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Lưu nháp biên bản. {@code expectedVersion} là phiên bản client đang sửa (0 khi chưa lưu lần nào); lệch ⇒ có người
 * lưu trước, báo xung đột thay vì ghi đè.
 */
public record InspectionSaveRequest(
        @NotNull Integer expectedVersion,
        Map<String, Object> header,
        @NotNull @Size(max = 400) List<Map<String, Object>> rows) {}
