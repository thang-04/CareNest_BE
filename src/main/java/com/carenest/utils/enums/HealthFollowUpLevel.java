package com.carenest.utils.enums;

/**
 * Mức theo dõi sức khỏe của trẻ, lấy từ kết luận của bác sĩ (HLT-10, user chốt 2026-10-31; phân loại WHO hoãn):
 * chưa có kết luận, bình thường, cần theo dõi, theo dõi sát, vấn đề đã biết ổn định.
 */
public enum HealthFollowUpLevel {
    UNAVAILABLE,
    NONE,
    WATCH,
    CLOSE_WATCH,
    KNOWN_STABLE
}
