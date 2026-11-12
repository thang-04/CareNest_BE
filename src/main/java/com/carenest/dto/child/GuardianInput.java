package com.carenest.dto.child;

/**
 * Phụ huynh trên form (AUTH-18): quan hệ theo mã {@code GuardianRelationship} (6 loại), SĐT nhận cả +84/khoảng trắng,
 * {@code idNumber} = CCCD 12 số, email tùy chọn. {@code id} chỉ dùng ở Sửa hồ sơ (phụ huynh đã có trên hồ sơ); tiếp nhận,
 * nhập Excel bỏ qua. Dữ liệu cá nhân: không log.
 */
public record GuardianInput(
        String fullName,
        String relation,
        String phone,
        String idNumber,
        String email,
        Boolean accountRecipient,
        Long id) {
    public GuardianInput(String fullName, String relation, String phone, String idNumber, String email) {
        this(fullName, relation, phone, idNumber, email, null, null);
    }

    public GuardianInput(
            String fullName, String relation, String phone, String idNumber, String email, Boolean accountRecipient) {
        this(fullName, relation, phone, idNumber, email, accountRecipient, null);
    }

    @Override
    public String toString() {
        return "GuardianInput[relation=" + relation + ", id=" + id + "]";
    }
}
