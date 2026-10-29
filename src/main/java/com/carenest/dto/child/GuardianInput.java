package com.carenest.dto.child;

/**
 * Phụ huynh trên form (AUTH-18): quan hệ theo mã {@code GuardianRelationship} (6 loại), SĐT nhận cả +84/khoảng trắng,
 * {@code idNumber} = CCCD 12 số, email tùy chọn. Dữ liệu cá nhân: không log.
 */
public record GuardianInput(String fullName, String relation, String phone, String idNumber, String email) {

    @Override
    public String toString() {
        return "GuardianInput[relation=" + relation + "]";
    }
}
