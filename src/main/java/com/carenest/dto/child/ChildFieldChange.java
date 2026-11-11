package com.carenest.dto.child;

/**
 * Một ô đổi khi Sửa hồ sơ (lịch sử {@code changes}, mục 3.7 plan tiếp nhận): đường dẫn ô như lỗi theo ô, nhãn tiếng Việt,
 * giá trị trước/sau dạng chữ. Số định danh trẻ, CCCD phụ huynh chỉ ghi 3 số cuối.
 */
public record ChildFieldChange(String field, String label, String from, String to) {
    @Override
    public String toString() {
        return "ChildFieldChange[field=" + field + "]";
    }
}
