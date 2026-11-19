package com.carenest.dto.child;

import com.carenest.utils.enums.SmsStatus;

/**
 * Tài khoản phụ huynh vừa đổi theo hồ sơ (mục 4.2 plan tiếp nhận). {@code phoneChanged} luôn false từ 2026-11-10: đổi
 * SĐT thành yêu cầu chờ duyệt; {@code smsStatus} null (sửa họ tên/email/CCCD không gửi SMS).
 */
public record GuardianAccountChangeResponse(
        Long userId, String fullName, String phone, boolean phoneChanged, SmsStatus smsStatus) {
    @Override
    public String toString() {
        return "GuardianAccountChangeResponse[userId=" + userId + "]";
    }
}
