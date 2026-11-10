package com.carenest.dto.health;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Mở lại đợt/kỳ đã đóng: bắt buộc lý do; đợt khám hoặc kỳ cân đo đã hết hạn cần ngày kết thúc mới từ hôm nay (HLT-17).
 */
public record HealthCheckReopenRequest(
        @NotBlank @Size(max = 300) String reason, LocalDate endDate) {
    public HealthCheckReopenRequest(String reason) {
        this(reason, null);
    }
}
