package com.carenest.service.eduplan.impl;

import java.time.LocalDate;

/** EDU-97: hạn xử lý lượt REVIEW tách biệt với hạn gửi và khóa sửa. */
final class EduReviewDeadline {
    record Info(LocalDate lockFrom, LocalDate reopenUntil, boolean locked) {}

    private EduReviewDeadline() {}

    static Info of(LocalDate submittedOn, int graceDays, LocalDate reopenUntil, LocalDate today) {
        if (submittedOn == null) return new Info(null, reopenUntil, false);
        if (reopenUntil != null)
            return new Info(reopenUntil.plusDays(1), reopenUntil, !today.isBefore(reopenUntil.plusDays(1)));
        LocalDate lockFrom = submittedOn.plusDays(Math.max(0, graceDays));
        return new Info(lockFrom, null, !today.isBefore(lockFrom));
    }
}
