package com.carenest.dto.health;

import com.carenest.utils.enums.HealthBmiStatus;
import com.carenest.utils.enums.HealthCheckResult;
import com.carenest.utils.enums.HealthConclusionCategory;
import com.carenest.utils.enums.HealthExamStatus;
import com.carenest.utils.enums.HealthFollowUpLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Danh sách trẻ của một lớp trong đợt/kỳ kèm kết quả; {@code canRecord}: GV phụ trách lớp, đợt/kỳ đang diễn ra. */
public record HealthCheckClassResponse(
        HealthCheckResponse campaign, HealthCheckClassRef cls, List<HealthCheckChild> children, boolean canRecord) {

    public record HealthCheckClassRef(Long id, String name) {}

    /**
     * Một trẻ trong đợt/kỳ. Phiếu khám nháp: {@code result} IN_PROGRESS (đợt đã đóng: NOT_CONCLUDED), chưa có mức. {@code
     * previous}: lần khám trước (đợt khám); {@code bmi}, {@code bmiStatus}: BMI và mức BMI theo tuổi của số đo kỳ cân đo
     * (HLT-18).
     */
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
            BigDecimal bmi,
            HealthBmiStatus bmiStatus,
            boolean published,
            HealthExamStatus examStatus,
            String source,
            HealthFollowUpLevel followUpLevel,
            HealthConclusionCategory conclusionCategory,
            HealthMeasurementResponse.HealthConclusionBrief conclusion,
            HealthExamResponse.HealthPreviousExam previous) {}
}
