package com.carenest.dto.health;

import com.carenest.utils.enums.HealthCheckResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Danh sách trẻ của một lớp trong đợt/kỳ kèm kết quả; {@code canRecord}: GV phụ trách lớp, đợt/kỳ đang diễn ra. */
public record HealthCheckClassResponse(
        HealthCheckResponse campaign, HealthCheckClassRef cls, List<HealthCheckChild> children, boolean canRecord) {

    public record HealthCheckClassRef(Long id, String name) {}

    public record HealthCheckChild(
            Long childId,
            String code,
            String fullName,
            LocalDate dateOfBirth,
            Long classId,
            String className,
            HealthCheckResult result,
            Long measurementId,
            LocalDate date,
            BigDecimal heightCm,
            BigDecimal weightKg,
            boolean published) {}
}
