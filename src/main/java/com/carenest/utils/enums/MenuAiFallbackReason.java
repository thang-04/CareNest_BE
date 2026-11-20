package com.carenest.utils.enums;

/** Lý do nháp dùng phương án theo quy tắc thay vì phương án AI (AI-03, AI-07). */
public enum MenuAiFallbackReason {
    /** AI tắt hoặc task không còn provider nào dùng được. */
    AI_DISABLED,
    /** Mọi provider lỗi (429, 5xx, mất kết nối). */
    AI_UNAVAILABLE,
    /** Hết thời gian chờ của task. */
    AI_TIMEOUT,
    /** Output sai cấu trúc hoặc vẫn còn lỗi chặn sau mọi vòng sửa. */
    AI_INVALID,
    /** Phương án AI hợp lệ nhưng điểm thấp hơn phương án theo quy tắc. */
    AI_NOT_BETTER
}
