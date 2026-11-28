package com.carenest.utils.enums;

/**
 * Trạng thái một ngày của trẻ trên App phụ huynh (Cần chốt 24/11/2026): có mặt / vắng có phép / vắng không phép / GV
 * chưa điểm danh / không phải ngày học / ngày trẻ chưa vào lớp hoặc đã nghỉ học.
 */
public enum ParentAttendanceStatus {
    PRESENT,
    ABSENT_EXCUSED,
    ABSENT,
    NOT_RECORDED,
    NO_SCHOOL,
    NOT_ENROLLED
}
