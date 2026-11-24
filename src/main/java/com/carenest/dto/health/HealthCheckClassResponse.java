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
     * (HLT-18). Trẻ chuyển lớp khi đợt/kỳ đang mở (G94-180/HLT-09): {@code movedTo} ở lớp cũ (chỉ xem, không tính tiến
     * độ), {@code measuredIn} ở lớp hiện tại khi kết quả đo ở lớp khác; {@code canEdit}: người xem nhập/sửa được kết quả
     * của dòng này.
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
            HealthExamResponse.HealthPreviousExam previous,
            boolean canEdit,
            MovedTo movedTo,
            MeasuredIn measuredIn) {}

    /** Lớp trẻ đã chuyển sang (học từ ngày {@code from}); kết quả ở lại lớp đã đo. */
    public record MovedTo(String className, String campusName, LocalDate from) {}

    /** Lớp, điểm trường và ngày của lần đo khi trẻ đo ở lớp khác (lớp cũ hoặc kỳ liên quan ở điểm trường cũ). */
    public record MeasuredIn(String className, String campusName, LocalDate date) {}
}
