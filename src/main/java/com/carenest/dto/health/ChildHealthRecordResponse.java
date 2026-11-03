package com.carenest.dto.health;

import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthFollowUpLevel;
import com.carenest.utils.enums.HealthLatestSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sổ sức khỏe phần module health có: các lần đo đã công bố (mới nhất trước; phiếu khám nháp không có), đợt/kỳ đang diễn
 * ra ở điểm trường của trẻ, ngày cuối còn đo vòng đầu (trẻ chưa quá 60 tháng), mức theo dõi hiện tại, phiếu theo dõi
 * đang mở, kết quả chuyên khoa. Hồ sơ, khai báo lấy ở API khác.
 */
public record ChildHealthRecordResponse(
        Long childId,
        LocalDate headCircumferenceUntil,
        boolean canRecord,
        HealthOpenCampaign openCampaign,
        List<HealthMeasurementResponse> measurements,
        HealthLatest latestHealth,
        HealthFollowUpResponse.HealthFollowUpBrief openFollowUp,
        List<HealthExternalResult> externalResults) {

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

    /**
     * Mức theo dõi hiện tại (null khi chưa có kết luận bác sĩ): lần khám gần nhất có kết luận; phiếu theo dõi đang mở ⇒
     * theo dõi sát; phiếu đóng theo lời bác sĩ (bình thường / vấn đề đã biết) sau lần khám đó thắng.
     *
     * @param resultOn ngày khám chuyên khoa gần nhất của phiếu đã đóng
     * @param closedWithoutResultOn phiếu đóng khi chưa có kết quả chuyên khoa, mức giữ theo lần khám
     */
    public record HealthLatest(
            LocalDate date,
            HealthFollowUpLevel followUpLevel,
            String campaignName,
            HealthLatestSource source,
            String knownCondition,
            LocalDate resultOn,
            Long followUpId,
            LocalDate closedWithoutResultOn) {}

    /** Kết quả khám chuyên khoa ở mọi phiếu theo dõi của trẻ, mới nhất trước. */
    public record HealthExternalResult(
            Long followUpId,
            LocalDate on,
            String note,
            String facility,
            List<HealthFollowUpResponse.HealthFollowUpImageView> images,
            LocalDate nextVisitOn,
            String byName,
            Instant at) {}
}
