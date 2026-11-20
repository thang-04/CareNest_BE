package com.carenest.dto.meals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Áp dụng hợp đồng mới cho NCC trên đường dẫn (NUT-27): cập nhật hợp đồng và tạo mức giá với NCC này cho các thực phẩm
 * được chọn, hiệu lực từ ngày bắt đầu.
 */
public record SupplierContractRequest(
        @Size(max = 50) String contractNo,
        @NotNull LocalDate contractStart,
        @NotNull LocalDate contractEnd,
        @NotEmpty List<@Valid Line> lines) {

    public record Line(
            @NotNull Long foodId, @NotNull @DecimalMin("0") BigDecimal price) {}
}
