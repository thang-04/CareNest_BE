package com.carenest.dto.health;

import com.carenest.utils.enums.HealthConclusionMode;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lưu phiếu khám. {@code conclude} false: lưu nháp (không kiểm đủ phiếu, không công bố); true: kết luận, công bố. Sửa
 * phiếu đã kết luận chỉ bằng kết luận lại kèm {@code reason}. {@code conclusionMode}: mẫu kết luận người nhập đang thấy,
 * khác mẫu hiện tại (phiếu theo dõi vừa đổi) ⇒ 409.
 */
public record HealthExamRequest(
        LocalDate date,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal headCircumferenceCm,
        HealthExamForm exam,
        boolean conclude,
        String reason,
        HealthConclusionMode conclusionMode) {}
