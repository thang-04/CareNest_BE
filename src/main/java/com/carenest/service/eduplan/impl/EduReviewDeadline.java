package com.carenest.service.eduplan.impl;

import java.time.LocalDate;

/**
 * EDU-109 (9): khóa duyệt dùng đúng phép tính Quá hạn của hồ sơ — một hạn hiệu lực chung cho gửi và duyệt (hạn HT mở
 * gần nhất, không có thì hạn gốc). Không còn mốc chụp lúc gửi (submissionCutoff).
 */
final class EduReviewDeadline {
    /** lockFrom = hạn hiệu lực; reopenUntil = hạn HT mở gần nhất (null nếu chưa mở). */
    record Info(LocalDate lockFrom, LocalDate reopenUntil, boolean locked) {}

    private EduReviewDeadline() {}

    static Info of(EduDeadline.Info deadline) {
        return new Info(deadline.effective(), deadline.extendedTo(), EduDeadline.OVERDUE.equals(deadline.state()));
    }
}
