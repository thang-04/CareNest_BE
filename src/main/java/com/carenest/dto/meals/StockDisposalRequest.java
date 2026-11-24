package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Phiếu xuất kho khô (NUT-29, NUT-30). {@code supplierId} bắt buộc khi trả NCC, bỏ qua khi xuất hủy. {@code quantity}
 * theo đơn vị gốc của thực phẩm (g/ml) như phiếu nhập.
 */
public record StockDisposalRequest(
        @NotNull Long campusId,
        Long supplierId,
        @NotBlank @Size(max = 500) String reason,
        @NotEmpty @Valid List<DisposalLineInput> lines) {

    public record DisposalLineInput(
            @NotNull Long lotId, @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal quantity) {}
}
