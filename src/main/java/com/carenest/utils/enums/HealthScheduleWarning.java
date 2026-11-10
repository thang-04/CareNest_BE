package com.carenest.utils.enums;

/** Cảnh báo lịch khi tạo/sửa đợt/kỳ (user chốt 2026-10-31): không chặn lưu. */
public enum HealthScheduleWarning {
    /** Ngày bắt đầu cách đợt/kỳ khác chưa hủy cùng điểm trường dưới 2 tháng 15 ngày. */
    TOO_CLOSE,
    /** Đợt khám mới mà năm học đã có đợt khám chưa hủy. */
    EXAM_IN_SCHOOL_YEAR
}
