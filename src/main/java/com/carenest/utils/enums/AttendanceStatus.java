package com.carenest.utils.enums;

/** Trạng thái điểm danh một trẻ trong ngày; vắng (có phép / không phép) thì không ăn bữa nào. */
public enum AttendanceStatus {
    PRESENT,
    EXCUSED,
    UNEXCUSED;

    public boolean isAbsent() {
        return this != PRESENT;
    }
}
