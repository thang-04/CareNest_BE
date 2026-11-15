package com.carenest.dto.account;

/**
 * Gửi yêu cầu đổi SĐT một phụ huynh trên hồ sơ trẻ (G94-180/AUTH-21 sửa 2026-11-11): ô SĐT ở Sửa hồ sơ khóa, đổi số chỉ
 * qua yêu cầu. {@code newPhone} chuẩn hóa như form tiếp nhận; {@code reason} bắt buộc, tối đa 300 ký tự.
 */
public record GuardianPhoneChangeCreateRequest(String newPhone, String reason) {
    @Override
    public String toString() {
        return "GuardianPhoneChangeCreateRequest[newPhone=***]";
    }
}
