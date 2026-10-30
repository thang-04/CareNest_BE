package com.carenest.dto.health;

import com.carenest.utils.enums.HealthCheckKind;
import java.time.LocalDate;
import java.util.List;

/**
 * Sổ sức khỏe phần module health có: các lần đo (mới nhất trước), đợt/kỳ đang diễn ra ở điểm trường của trẻ, ngày cuối
 * còn đo vòng đầu (trẻ đủ 60 tháng). Hồ sơ, khai báo, phiếu theo dõi lấy ở API khác.
 */
public record ChildHealthRecordResponse(
        Long childId,
        LocalDate headCircumferenceUntil,
        boolean canRecord,
        HealthOpenCampaign openCampaign,
        List<HealthMeasurementResponse> measurements) {

    /** {@code resultStatus}: {@code RECORDED} khi trẻ đã có kết quả trong đợt/kỳ, null khi chưa. */
    public record HealthOpenCampaign(
            Long id,
            HealthCheckKind kind,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            Long resultMeasurementId,
            String resultStatus) {}
}
