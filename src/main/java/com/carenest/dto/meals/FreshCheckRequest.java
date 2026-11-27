package com.carenest.dto.meals;

import com.carenest.utils.enums.FreshCheckHandling;
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
 * đủ/thiếu/thừa; {@code receivedQty} (đơn vị gốc g/ml) không bắt buộc. NCC ghi theo dòng (NUT-12): dòng để trống ⇒
 * {@code supplierId} cấp lần kiểm (client cũ) ⇒ NCC của thực phẩm tại ngày kiểm (NUT-27). Lần 2 theo bản cân đối
 * ({@code handlingRequired}): dòng lệch hàng đã nhận lần 1 phải chọn {@code handlingAction} (gọi thêm, trả lại, giữ
 * dùng) kèm {@code handlingQty} khi gọi thêm hoặc trả lại; BE tính số thực nhận và đủ/thiếu/thừa.
 */
public record FreshCheckRequest(
        @Size(max = 500) String note, @NotNull @Valid List<FreshCheckLineInput> lines, Long supplierId) {

    public record FreshCheckLineInput(
            @NotNull Long foodId,

            @PositiveOrZero @Digits(integer = 12, fraction = 2)
            BigDecimal receivedQty,

            FreshCheckResult result,
            @Size(max = 300) String note,
            Long supplierId,
            FreshCheckHandling handlingAction,

            @PositiveOrZero @Digits(integer = 12, fraction = 2)
            BigDecimal handlingQty) {

        public FreshCheckLineInput(
                Long foodId, BigDecimal receivedQty, FreshCheckResult result, String note, Long supplierId) {
            this(foodId, receivedQty, result, note, supplierId, null, null);
        }
    }
}
