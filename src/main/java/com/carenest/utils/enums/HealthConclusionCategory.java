package com.carenest.utils.enums;

/**
 * Nhóm kết luận của phiếu khám: bình thường, có vấn đề sức khỏe, chuyển khám chuyên khoa, nguy cơ lao; với mẫu theo
 * dõi: không có bất thường mới, có bất thường mới.
 */
public enum HealthConclusionCategory {
    NORMAL,
    ISSUE,
    REFERRAL,
    TB_RISK,
    FOLLOWING_NO_NEW,
    FOLLOWING_NEW
}
