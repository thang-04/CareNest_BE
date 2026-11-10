package com.carenest.utils.enums;

/**
 * Kết quả của trẻ trong đợt/kỳ: chưa có, đã có, vắng (đợt/kỳ đã đóng khi trẻ chưa có kết quả); phiếu khám còn nháp là
 * đang khám, đóng đợt khi còn nháp là chưa kết luận.
 */
public enum HealthCheckResult {
    NOT_RECORDED,
    IN_PROGRESS,
    RECORDED,
    NOT_CONCLUDED,
    ABSENT
}
