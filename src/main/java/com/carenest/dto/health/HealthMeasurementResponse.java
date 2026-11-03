package com.carenest.dto.health;

import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthConclusionCategory;
import com.carenest.utils.enums.HealthExamStatus;
import com.carenest.utils.enums.HealthFollowUpEffect;
import com.carenest.utils.enums.HealthFollowUpLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Một lần đo. {@code source} = {@code MEASUREMENT_ONLY} (số đo của kỳ cân đo, chưa có kết luận bác sĩ) hoặc {@code EXAM}
 * (phiếu khám Phụ lục II); {@code locked}: đợt/kỳ đã đóng hoặc hủy, không sửa được. Lưu là công bố; phiếu khám nháp chưa
 * công bố ({@code published} false). Mức theo dõi chỉ lấy từ kết luận bác sĩ (HLT-10, phân loại WHO hoãn).
 *
 * @param headCircumferenceStatus vòng đầu theo bác sĩ ({@code NORMAL}, {@code LARGE}, {@code SMALL}), {@code
 *     NOT_MEASURED} khi trẻ quá 60 tháng; null với số đo của kỳ cân đo
 * @param followUpId phiếu theo dõi chứa lần khám này
 * @param parentContact trao đổi phụ huynh về kết luận "theo dõi sát" của lần khám
 * @param reviewPoints "Điểm cần bác sĩ xem lại", chỉ có ở API phiếu khám
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
        List<HealthMeasurementEditView> edits,
        HealthExamStatus examStatus,
        HealthExamForm exam,
        Instant concludedAt,
        String headCircumferenceStatus,
        HealthFollowUpLevel followUpLevel,
        HealthConclusionCategory conclusionCategory,
        HealthConclusionBrief conclusion,
        Long followUpId,
        HealthFollowUpEffect followUpEffect,
        HealthParentContact parentContact,
        HealthExamReviewPoints reviewPoints) {

    public static final String SOURCE_MEASUREMENT_ONLY = "MEASUREMENT_ONLY";
    public static final String SOURCE_EXAM = "EXAM";

    public record HealthMeasurementEditView(
            Instant at, Long userId, String userName, String reason, List<HealthMeasurementChange> changes) {}

    /** Một ô đổi khi sửa: {@code label} là tên ô tiếng Việt, {@code from}/{@code to} là chuỗi hiển thị. */
    public record HealthMeasurementChange(String field, String label, String from, String to) {}

    /** Kết luận bác sĩ rút gọn của phiếu khám đã kết luận. */
    public record HealthConclusionBrief(
            String doctorName,
            String issueText,
            boolean tbRisk,
            String action,
            String adviceText,
            String specialty,
            String issueKind,
            String followUpFinding) {}

    /**
     * {@code atExam}: bác sĩ đã trao đổi với người đi cùng tại buổi khám ({@code opinion} = ý kiến phụ huynh); không thì
     * lần trao đổi đầu tiên GV ghi sau lần khám.
     */
    public record HealthParentContact(boolean contacted, boolean atExam, LocalDate on, String opinion) {}
}
