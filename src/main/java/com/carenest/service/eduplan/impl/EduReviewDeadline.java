package com.carenest.service.eduplan.impl;

import java.time.LocalDate;

/** EDU-97: hạn xử lý lượt REVIEW tách biệt với hạn gửi và khóa sửa. */
final class EduReviewDeadline {
    record Info(LocalDate lockFrom, LocalDate reopenUntil, boolean locked) {}

    private EduReviewDeadline() {}

    /**
     * Mốc khóa duyệt = hạn gửi lúc gửi (submissionCutoff). Không có hạn, hoặc gửi sau mốc đó (không áp hạn, vd. lớp mở
     * giữa năm) ⇒ null, lượt duyệt không khóa theo thời gian.
     */
    static LocalDate baseOf(LocalDate submittedOn, LocalDate cutoff) {
        return cutoff == null || (submittedOn != null && submittedOn.isAfter(cutoff)) ? null : cutoff;
    }

    /** EDU-109: khóa duyệt từ đúng ngày hạn {@code base}, không ngày duyệt thêm; HT mở hạn ⇒ duyệt hết ngày reopenUntil. */
    static Info of(LocalDate base, LocalDate reopenUntil, LocalDate today) {
        if (base == null) return new Info(null, reopenUntil, false);
        if (reopenUntil != null)
            return new Info(reopenUntil.plusDays(1), reopenUntil, !today.isBefore(reopenUntil.plusDays(1)));
        return new Info(base, null, !today.isBefore(base));
    }
}
