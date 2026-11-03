package com.carenest.dto.health;

import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.HealthConclusionMode;
import com.carenest.utils.enums.HealthFollowUpLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Phiếu khám Phụ lục II của trẻ trong đợt khám. Đã lưu ⇒ {@code measurement} (nháp: hành chính, tiền sử còn trống điền
 * lại từ hồ sơ, khai báo, không lưu); chưa lưu ⇒ {@code draft} điền sẵn. {@code reference} theo tuổi tại ngày khám.
 *
 * @param conclusionMode mẫu kết luận: STANDARD; FOLLOW_UP chỉ còn ở phiếu đã kết luận theo mẫu phiếu theo dõi cũ (HLT-16
 *     đã bỏ)
 * @param knownCondition vấn đề đã biết của trẻ (lần khám gần nhất mức "ổn định"), để hỏi vấn đề mới hay đã biết
 * @param doctorNames tên bác sĩ đã ghi trong đợt, để chọn nhanh
 * @param canEdit GV lớp, đợt đang diễn ra
 * @param locked đợt không còn diễn ra (đã đóng, đã hủy, chưa bắt đầu)
 */
public record HealthExamResponse(
        HealthConclusionMode conclusionMode,
        String knownCondition,
        HealthExamCampaign campaign,
        HealthExamChild child,
        HealthMeasurementResponse measurement,
        HealthExamDraft draft,
        HealthExamReference reference,
        HealthPreviousExam previous,
        List<String> doctorNames,
        boolean canEdit,
        boolean locked) {

    public record HealthExamCampaign(
            Long id, String name, LocalDate startDate, LocalDate endDate, String examiner, HealthCheckStatus status) {}

    /** Trẻ và phụ huynh trên hồ sơ (chọn người đi cùng theo thứ tự trong {@code guardians}). */
    public record HealthExamChild(
            Long id,
            String code,
            String fullName,
            Gender gender,
            LocalDate dateOfBirth,
            Long classId,
            String className,
            List<HealthExamGuardian> guardians) {}

    /** {@code relation}: nhãn quan hệ trên phiếu (Cha, Mẹ, Ông/bà, Anh/chị, Khác). */
    public record HealthExamGuardian(String fullName, String relation, String phone, String idNumber) {}

    public record HealthExamDraft(
            LocalDate date,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal headCircumferenceCm,
            HealthExamForm exam) {}

    /** Lần khám trước (đợt khám, đã có kết quả) để bác sĩ so sánh. */
    public record HealthPreviousExam(
            LocalDate date,
            String campaignName,
            HealthFollowUpLevel followUpLevel,
            HealthMeasurementResponse.HealthConclusionBrief conclusion) {}
}
