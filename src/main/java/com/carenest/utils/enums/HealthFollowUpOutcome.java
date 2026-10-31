package com.carenest.utils.enums;

/**
 * Kết quả đóng phiếu theo dõi theo lời bác sĩ: bình thường (mức về không theo dõi), vấn đề đã biết (ghi vào tiền sử, mức
 * ổn định), chưa có kết quả chuyên khoa (giữ mức của lần khám).
 */
public enum HealthFollowUpOutcome {
    DOCTOR_NORMAL,
    KNOWN_CONDITION,
    NO_SPECIALIST_RESULT
}
