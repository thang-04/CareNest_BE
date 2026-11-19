package com.carenest.dto.child;

import java.util.List;

/**
 * Một phụ huynh trên hồ sơ trẻ để gửi/duyệt yêu cầu đổi SĐT (G94-180/AUTH-21 sửa 2026-11-11). {@code campusId} = điểm
 * trường hiện tại của trẻ (người duyệt theo điểm trường này); {@code accountId} = tài khoản đang liên kết với trẻ (null =
 * chưa kích hoạt); {@code otherGuardianPhones} = SĐT các phụ huynh khác của cùng trẻ. Dữ liệu cá nhân: không log.
 */
public record GuardianPhoneTarget(
        Long childId,
        String childName,
        Long campusId,
        Long guardianId,
        String fullName,
        String citizenId,
        String phone,
        Long accountId,
        List<String> otherGuardianPhones) {
    public GuardianPhoneTarget {
        otherGuardianPhones = List.copyOf(otherGuardianPhones);
    }

    @Override
    public String toString() {
        return "GuardianPhoneTarget[childId=" + childId + ", guardianId=" + guardianId + "]";
    }
}
