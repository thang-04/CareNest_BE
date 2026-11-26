package com.carenest.dto.account;

/**
 * Giáo viên đang dạy lớp của trẻ cho App phụ huynh (PAR-04: chỉ để gọi điện, không chat). {@code phone} = tên đăng nhập
 * khi là SĐT, null nếu không; {@code homeroom}: cô chủ nhiệm. Không trả ID tài khoản.
 */
public record ChildTeacherResponse(String fullName, String phone, boolean homeroom) {
    @Override
    public String toString() {
        return "ChildTeacherResponse[homeroom=" + homeroom + "]";
    }
}
