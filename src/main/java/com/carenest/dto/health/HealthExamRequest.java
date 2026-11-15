package com.carenest.dto.health;

import com.carenest.utils.enums.HealthConclusionMode;
import java.math.BigDecimal;

/**
 * Lưu phiếu khám. {@code conclude} false: lưu nháp (không kiểm đủ phiếu, không công bố); true: kết luận, công bố. Sửa
 * phiếu đã kết luận chỉ bằng kết luận lại kèm {@code reason}. {@code conclusionMode}: mẫu kết luận người nhập đang thấy,
 * khác mẫu hiện tại của phiếu ⇒ 409. Ngày khám hệ thống ghi khi lưu lần đầu, không sửa (HLT-08, user chốt 2026-11-04).
 */
public record HealthExamRequest(
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal headCircumferenceCm,
        HealthExamForm exam,
        boolean conclude,
        String reason,
        HealthConclusionMode conclusionMode) {}
