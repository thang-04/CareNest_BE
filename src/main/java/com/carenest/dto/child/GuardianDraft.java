package com.carenest.dto.child;

import com.carenest.utils.enums.GuardianRelationship;

/**
 * Phụ huynh đã chuẩn hóa: SĐT 10 số bắt đầu 0 (= tên đăng nhập), CCCD 12 số, email chữ thường hoặc null.
 * {@code position} = thứ tự trên hồ sơ (0–2). Dữ liệu cá nhân: không log.
 */
public record GuardianDraft(
        int position,
        String fullName,
        GuardianRelationship relationship,
        String phone,
        String citizenId,
        String email) {

    /** Thông tin theo tài khoản có sẵn (họ tên, SĐT, email), giữ quan hệ và CCCD của hồ sơ. */
    public GuardianDraft withAccountInfo(String accountFullName, String accountPhone, String accountEmail) {
        return new GuardianDraft(position, accountFullName, relationship, accountPhone, citizenId, accountEmail);
    }

    @Override
    public String toString() {
        return "GuardianDraft[position=" + position + ", relationship=" + relationship + "]";
    }
}
