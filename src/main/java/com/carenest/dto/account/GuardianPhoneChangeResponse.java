package com.carenest.dto.account;

import com.carenest.utils.enums.PhoneChangeStatus;
import com.carenest.utils.enums.SmsStatus;
import java.time.Instant;

/**
 * Yêu cầu đổi SĐT phụ huynh (G94-180/AUTH-21). {@code canDecide} = người xem duyệt/từ chối được (có quyền ở điểm trường
 * của trẻ, không phải người gửi khi duyệt). {@code smsStatus} = SMS tên đăng nhập mới sau khi duyệt. Không chứa mật khẩu.
 */
public record GuardianPhoneChangeResponse(
        Long id,
        Long childId,
        String childCode,
        String childName,
        Long campusId,
        Long guardianId,
        Long userId,
        String guardianName,
        String oldPhone,
        String newPhone,
        PhoneChangeStatus status,
        String reason,
        Long requestedBy,
        String requestedByName,
        Instant requestedAt,
        Long decidedBy,
        String decidedByName,
        Instant decidedAt,
        String decisionNote,
        SmsStatus smsStatus,
        boolean canDecide) {
    @Override
    public String toString() {
        return "GuardianPhoneChangeResponse[id=" + id + ", status=" + status + "]";
    }
}
