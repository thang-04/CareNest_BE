package com.carenest.utils.enums;

/** Trạng thái yêu cầu đổi SĐT phụ huynh đã có tài khoản (G94-180/AUTH-21, V87). */
public enum PhoneChangeStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Có yêu cầu mới cho cùng tài khoản thay thế (Sửa hồ sơ lần sau đổi sang số khác). */
    CANCELLED
}
