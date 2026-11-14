package com.carenest.dto.health;

import com.carenest.utils.enums.HealthBmiStatus;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthConclusionCategory;
import com.carenest.utils.enums.HealthFollowUpLevel;
import com.carenest.utils.enums.HealthTrendWarningCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sức khỏe của trẻ phụ huynh được xem (PAR-01, PAR-03, HLT-11): chỉ kết quả đã công bố (số đo kỳ cân đo, phiếu khám đã
 * kết luận), cảnh báo hiện tại, chuỗi tăng trưởng cho biểu đồ (HLT-02, HLT-03) và giải thích xu hướng đã duyệt (HLT-05).
 * Không có lịch sử sửa, người nhập, phiếu nháp. Tên record lồng duy nhất (springdoc).
 */
public record PublishedHealthResponse(
        Long childId,
        List<PublishedHealthResult> results,
        List<GrowthPoint> growth,
        ChildHealthRecordResponse.HealthLatestExam latestExam,
        ChildHealthRecordResponse.HealthLatestBmi latestBmi,
        ApprovedInterpretation interpretation) {

    /** Một kết quả đã công bố, mới nhất trước. {@code conclusion} chỉ có ở phiếu khám đã kết luận. */
    public record PublishedHealthResult(
            Long id,
            LocalDate date,
            Integer ageMonths,
            String source,
            HealthCheckKind campaignKind,
            String campaignName,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal headCircumferenceCm,
            BigDecimal bmi,
            HealthBmiStatus bmiStatus,
            BigDecimal bmiZScore,
            String note,
            HealthFollowUpLevel followUpLevel,
            HealthConclusionCategory conclusionCategory,
            HealthMeasurementResponse.HealthConclusionBrief conclusion,
            Instant publishedAt) {}

    /** Điểm biểu đồ tăng trưởng (cũ nhất trước), z-score chuẩn WHO làm tròn 2 chữ số; null khi ngoài bảng. */
    public record GrowthPoint(
            Long measurementId,
            LocalDate date,
            Integer ageMonths,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal bmi,
            BigDecimal weightForAgeZ,
            BigDecimal heightForAgeZ,
            BigDecimal bmiZScore) {}

    /** Giải thích xu hướng đã được nhân sự duyệt (HLT-05); null khi chưa có. */
    public record ApprovedInterpretation(Long id, String summary, List<TrendWarning> warnings, Instant approvedAt) {}

    /** Cảnh báo xu hướng tính xác định từ số đo (HLT-02): {@code level} warning | info; {@code code} null ở bản lưu trước HLT-21. */
    public record TrendWarning(HealthTrendWarningCode code, String level, String text) {}
}
