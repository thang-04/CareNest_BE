package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Phiếu nhập kho khô (nháp). {@code quantity} theo đơn vị gốc của thực phẩm (g/ml), {@code unitPrice} theo đơn vị mua. */
public record StockReceiptRequest(
        @NotNull Long campusId,
        @NotNull Long supplierId,
        @NotNull LocalDate receivedDate,
        @Size(max = 500) String note,
        @NotEmpty @Valid List<ReceiptLineInput> lines) {

    public record ReceiptLineInput(
            @NotNull Long foodId,
            @NotBlank @Size(max = 50) String lotNo,
            @NotNull LocalDate expiryDate,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @PositiveOrZero BigDecimal unitPrice) {}
}
