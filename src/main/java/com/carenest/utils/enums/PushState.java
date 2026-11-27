package com.carenest.utils.enums;

/** Trạng thái đẩy của một thông báo (hàng chờ trong bảng notifications, spec 019). */
public enum PushState {
    /** Chờ đẩy hoặc chờ thử lại ({@code push_next_at}). */
    PENDING,
    /** Ít nhất một thiết bị đã nhận. */
    SENT,
    /** Ngừng đẩy: quá hạn thử lại hoặc mọi thiết bị lỗi vĩnh viễn. */
    FAILED,
    /** Không đẩy: người nhận không có thiết bị, hoặc thông báo có trước khi triển khai. */
    SKIPPED
}
