package com.carenest.dto.health;

import com.carenest.utils.enums.HealthBmiStatus;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthConclusionCategory;
import com.carenest.utils.enums.HealthFollowUpLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sổ sức khỏe phần module health có: các lần đo đã công bố kèm cảnh báo (mới nhất trước; phiếu khám nháp không có), đợt/kỳ
 * đang diễn ra ở điểm trường của trẻ, cảnh báo hiện tại (HLT-19): kết luận bác sĩ của lần khám gần nhất, mức BMI của lần
 * đo gần nhất trong kỳ cân đo. Hồ sơ, khai báo lấy ở API khác.
 */
public record ChildHealthRecordResponse(
        Long childId,
        boolean canRecord,
        HealthOpenCampaign openCampaign,
        List<HealthMeasurementResponse> measurements,
        HealthLatestExam latestExam,
        HealthLatestBmi latestBmi) {

    /**
     * {@code resultStatus}: kỳ cân đo {@code RECORDED} khi trẻ đã có số đo; đợt khám {@code DRAFT} hoặc
     * {@code CONCLUDED} theo phiếu khám; null khi chưa có.
     */
    public record HealthOpenCampaign(
            Long id,
            HealthCheckKind kind,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            Long resultMeasurementId,
            String resultStatus) {}

    /** Lần khám gần nhất có kết luận bác sĩ (HLT-10): mức theo dõi, nhóm kết luận, kết luận rút gọn. */
    public record HealthLatestExam(
            Long measurementId,
            Long campaignId,
            String campaignName,
            LocalDate date,
            HealthFollowUpLevel followUpLevel,
            HealthConclusionCategory conclusionCategory,
            HealthMeasurementResponse.HealthConclusionBrief conclusion) {}

    /** Lần đo gần nhất trong kỳ cân đo có mức BMI theo tuổi (HLT-18). */
    public record HealthLatestBmi(
            Long measurementId,
            Long campaignId,
            String campaignName,
            LocalDate date,
            Integer ageMonths,
            BigDecimal bmi,
            HealthBmiStatus bmiStatus,
            BigDecimal bmiZScore) {}
}
