package com.carenest.utils.enums;

/** Loại thông báo gửi người dùng; App dùng để mở đúng màn hình. */
public enum NotificationType {
    /** Trẻ đã được trả cho người đón (PCK-04). */
    CHILD_PICKED_UP,
    /** Sổ báo ăn đã xác nhận thay đổi (sửa điểm danh sau mở lại), PHT cần xác nhận lại (NUT-03). */
    MEAL_COUNT_RECONFIRM,
    /** Trẻ nghỉ học, báo giáo viên lớp (G94-180/CHD-03). */
    CHILD_WITHDRAWN,
    /** Sửa hồ sơ trẻ đổi SĐT của phụ huynh đã có tài khoản, chờ PHT/HT duyệt (G94-180/AUTH-21). */
    GUARDIAN_PHONE_CHANGE_REQUESTED,
    /** Yêu cầu đổi SĐT phụ huynh đã được duyệt hoặc từ chối, báo người gửi yêu cầu. */
    GUARDIAN_PHONE_CHANGE_DECIDED
}
