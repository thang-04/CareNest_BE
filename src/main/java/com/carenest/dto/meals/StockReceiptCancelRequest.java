package com.carenest.dto.meals;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Hủy phiếu đã xác nhận bằng bút toán đảo: lý do bắt buộc. */
public record StockReceiptCancelRequest(
        @NotBlank @Size(max = 500) String reason) {}
