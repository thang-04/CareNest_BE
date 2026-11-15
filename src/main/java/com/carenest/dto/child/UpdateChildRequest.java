package com.carenest.dto.child;

/**
 * Sửa hồ sơ trẻ ({@code PUT /children/{id}}, mục 4.2 plan tiếp nhận): thông tin trẻ, hành chính, phụ huynh như form tiếp
 * nhận (không có khai báo sức khỏe, lớp); lý do bắt buộc, tối đa 300 ký tự.
 */
public record UpdateChildRequest(EnrollmentChildInput child, String reason) {
    @Override
    public String toString() {
        return "UpdateChildRequest[child=" + child + "]";
    }
}
