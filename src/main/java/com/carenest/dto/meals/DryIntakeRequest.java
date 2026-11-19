package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Một đợt hàng khô giao trong ngày (NUT-11, NUT-12). Dòng {@code passed} thành lô của phiếu nhập; dòng không đạt bắt
 * buộc {@code note} là biện pháp xử lý. {@code quantity} theo đơn vị gốc (g/ml), {@code unitPrice} theo đơn vị mua.
 */
public record DryIntakeRequest(
        @NotNull Long supplierId,
        @Size(max = 500) String note,
        @NotEmpty @Valid List<DryIntakeLineInput> lines) {

    public record DryIntakeLineInput(
            @NotNull Long foodId,
            @NotBlank @Size(max = 50) String lotNo,
            @NotNull LocalDate expiryDate,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal quantity,
            @NotNull @PositiveOrZero BigDecimal unitPrice,
            @NotNull Boolean passed,
            @Size(max = 300) String note) {}
}
