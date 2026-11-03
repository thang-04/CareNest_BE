package com.carenest.utils.enums;

/**
 * Kết quả tài khoản của từng phụ huynh khi lưu form tiếp nhận (AUTH-20): tạo mới và gửi SMS, liên kết tài khoản có
 * sẵn, hoặc chờ kích hoạt vì người lưu không có quyền quản lý tài khoản phụ huynh (P-18).
 */
public enum GuardianAccountResult {
    CREATED,
    LINKED,
    NOT_ACTIVATED
}
