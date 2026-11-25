package com.carenest.service.eduplan.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Hạn kế hoạch giáo dục chủ đề / kế hoạch giáo dục ngày (spec 003, data-model "Hạn"): tính khi đọc, không lưu.
 * Hạn = ngày bắt đầu học kỳ / chủ đề; mở hạn (HT/PHT) thay bằng hạn mới.
 */
final class EduDeadline {
    static final String NORMAL = "NORMAL";
    static final String NEAR = "NEAR";
    static final String OVERDUE = "OVERDUE";
    static final String ON_TIME = "ON_TIME";
    /** EDU-109: ban hành sau hạn — không nhãn hạn (bỏ "Ban hành trễ"). */
    static final String ISSUED = "ISSUED";

    static final int NEAR_DAYS = 7;

    record Info(LocalDate deadline, LocalDate extendedTo, Long daysLeft, String state) {
        /** EDU-109: không còn ngày thêm; hạn hiệu lực = hạn HT mở gần nhất, không có thì hạn gốc. */
        LocalDate effective() {
            return extendedTo != null ? extendedTo : deadline;
        }
    }

    private EduDeadline() {}

    /**
     * @param approved trạng thái hiện tại là Đã ban hành
     * @param firstRelease ngày ban hành lần đầu (null nếu chưa từng)
     * EDU-109 (9): quá hạn = hôm nay >= hạn hiệu lực và chưa ban hành; không còn ngoại lệ ngày mở lớp.
     */
    static Info of(
            LocalDate deadline, LocalDate extendedTo, LocalDate today, boolean approved, LocalDate firstRelease) {
        if (deadline == null) return new Info(null, null, null, NORMAL);
        LocalDate effective = extendedTo != null ? extendedTo : deadline;
        long daysLeft = ChronoUnit.DAYS.between(today, effective);
        String state;
        if (approved) state = firstRelease == null || firstRelease.isBefore(effective) ? ON_TIME : ISSUED;
        else if (daysLeft <= 0) state = OVERDUE;
        else if (daysLeft <= NEAR_DAYS) state = NEAR;
        else state = NORMAL;
        return new Info(deadline, extendedTo, daysLeft, state);
    }
}
