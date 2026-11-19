package com.carenest.utils.enums;

/** Trạng thái yêu cầu sửa hồ sơ trẻ của GV (CHD-11, V153). */
public enum ProfileChangeStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** GV gửi yêu cầu mới cùng loại cho cùng trẻ thay thế yêu cầu đang chờ. */
    CANCELLED
}
