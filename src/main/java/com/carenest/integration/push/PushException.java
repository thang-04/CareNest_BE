package com.carenest.integration.push;

import java.time.Duration;

/** Lỗi đẩy một thiết bị, đã phân loại để worker quyết định xóa thiết bị, thử lại hay bỏ. */
public class PushException extends RuntimeException {

    public enum Kind {
        /** Thiết bị không còn hợp lệ (gỡ App, token hỏng): xóa khỏi danh sách nhận. */
        INVALID_TOKEN,
        /** Lỗi tạm thời (quá tải, mạng, dịch vụ lỗi): thử lại sau. */
        RETRYABLE,
        /** Lỗi không thử lại được, giữ thiết bị (vd. iOS chưa có khóa APNs). */
        PERMANENT
    }

    private final Kind kind;
    private final transient Duration retryAfter;

    public PushException(Kind kind, String message, Duration retryAfter) {
        super(message);
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public PushException(Kind kind, String message) {
        this(kind, message, null);
    }

    public Kind kind() {
        return kind;
    }

    /** Thời gian chờ dịch vụ đẩy yêu cầu (header Retry-After), có thể null. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
