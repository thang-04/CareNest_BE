package com.carenest.utils.enums;

/**
 * Tác động của một lần khám lên phiếu theo dõi: mở phiếu, chỉ ghi nhận (không bất thường mới), mở vòng trao đổi mới,
 * gộp vào vòng đang chờ trao đổi.
 */
public enum HealthFollowUpEffect {
    OPENED,
    NOTED,
    NEW_ROUND,
    MERGED
}
