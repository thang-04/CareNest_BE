package com.carenest.dto.meals;

import com.carenest.utils.enums.FreshCheckResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Ghi kiểm nhận hàng tươi một lần: đủ một dòng cho mỗi thực phẩm cần nhận (không thiếu, không lạ). PHT chọn
 * đủ/thiếu/thừa; {@code receivedQty} (đơn vị gốc g/ml) không bắt buộc.
 */
public record FreshCheckRequest(
        @Size(max = 500) String note, @NotNull @Valid List<FreshCheckLineInput> lines) {

    public record FreshCheckLineInput(
            @NotNull Long foodId,

            @PositiveOrZero @Digits(integer = 12, fraction = 2)
            BigDecimal receivedQty,

            @NotNull FreshCheckResult result,
            @Size(max = 300) String note) {}
}
