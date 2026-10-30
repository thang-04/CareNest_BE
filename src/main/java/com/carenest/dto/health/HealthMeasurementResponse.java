package com.carenest.dto.health;

import com.carenest.utils.enums.HealthCheckKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Một lần đo. {@code source} = {@code MEASUREMENT_ONLY} (số đo, chưa có kết luận bác sĩ) tới khi có phiếu khám;
 * {@code locked}: đợt/kỳ đã đóng hoặc hủy, không sửa được. Lưu là công bố ({@code published} luôn true).
 */
public record HealthMeasurementResponse(
        Long id,
        Long childId,
        Long campaignId,
        String campaignName,
        HealthCheckKind campaignKind,
        Long classroomId,
        LocalDate date,
        Integer ageMonths,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal headCircumferenceCm,
        BigDecimal bmi,
        String note,
        String source,
        boolean published,
        Instant publishedAt,
        Long measuredBy,
        String measuredByName,
        boolean locked,
        List<HealthMeasurementEditView> edits) {

    public static final String SOURCE_MEASUREMENT_ONLY = "MEASUREMENT_ONLY";

    public record HealthMeasurementEditView(
            Instant at, Long userId, String userName, String reason, List<HealthMeasurementChange> changes) {}

    public record HealthMeasurementChange(String field, String from, String to) {}
}
