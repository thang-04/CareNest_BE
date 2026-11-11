package com.carenest.utils.enums;

/** Loại thông báo gửi người dùng; App dùng để mở đúng màn hình. */
public enum NotificationType {
    /** Trẻ đã được trả cho người đón (PCK-04). */
    CHILD_PICKED_UP,
    /** Sổ báo ăn đã xác nhận thay đổi (sửa điểm danh sau mở lại), PHT cần xác nhận lại (NUT-03). */
    MEAL_COUNT_RECONFIRM,
    /** Ngày điểm danh của lớp được mở lại (ATT-08, kể cả trả lại từ sổ suất ăn): giáo viên lớp kiểm tra, sửa. */
    ATTENDANCE_DAY_UNLOCKED
}
