package com.carenest.utils.enums;

/** Trạng thái thông báo của trường (spec 019): hẹn → đang gửi → đã gửi; hẹn → hủy. */
public enum AnnouncementStatus {
    SCHEDULED,
    SENDING,
    SENT,
    CANCELLED
}
