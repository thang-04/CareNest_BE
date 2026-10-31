package com.carenest.dto.health;

import java.util.List;

/**
 * "Điểm cần bác sĩ xem lại" của phiếu khám: gợi ý cho người nhập, không phải kết luận. Tính từ tuổi tại ngày khám, dấu
 * hiệu sinh tồn, vòng đầu, dinh dưỡng, phát triển, tiêm chủng, lâm sàng, tiền sử.
 *
 * @param reference nhãn nhóm tuổi của khoảng tham khảo sinh tồn
 */
public record HealthExamReviewPoints(
        List<HealthExamVitalPoint> vitals,
        HealthExamHint vitalsHint,
        HealthExamCombinedHint combined,
        List<HealthExamOtherPoint> others,
        String reference) {

    public record HealthExamVitalPoint(String key, String text) {}

    /** {@code level}: {@code NONE}, {@code WATCH}, {@code ALERT}. */
    public record HealthExamHint(int count, int total, String level, String text) {}

    /** {@code level}: {@code NONE}, {@code WATCH}, {@code REFERRAL}. */
    public record HealthExamCombinedHint(List<String> signs, int total, String level, String text) {}

    public record HealthExamOtherPoint(String text) {}
}
