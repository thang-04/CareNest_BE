package com.carenest.dto.attendance;

import com.carenest.utils.enums.HealthAction;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.HealthSymptom;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Một lần ghi nhận sức khỏe; requestId do FE sinh cho mỗi lần bấm lưu (gửi lặp trả lại bản đã lưu).
 * Thân nhiệt tùy chọn, hai chữ số và tối đa một số thập phân.
 */
public record DailyHealthRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotNull Long classId,
        @NotNull Long childId,
        @NotNull LocalDate date,

        @DecimalMin("10.0") @DecimalMax("99.9") @Digits(integer = 2, fraction = 1)
        BigDecimal temperature,

        @NotNull HealthObservation observation,
        List<HealthSymptom> symptoms,
        @Size(max = 500) String note,
        List<HealthAction> actions,
        @Valid Referral referral) {

    /** Chuyển cơ sở y tế: nơi chuyển, giờ chuyển (HH:mm), người đi kèm. */
    public record Referral(
            @Size(max = 150) String place,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            @Size(max = 100) String escort) {}
}
