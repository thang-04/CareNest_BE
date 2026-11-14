package com.carenest.service.observation.impl;

import com.carenest.utils.enums.EvaluationLockKind;

/** Đường dẫn Web của thông báo nhắc việc đánh giá (OBS-30); tham số r làm khóa "một lần" cho mỗi lời nhắc. */
final class AssessmentReminderLinks {

    private AssessmentReminderLinks() {}

    static String evaluation(EvaluationLockKind kind, Long classId, String periodKey) {
        String page =
                switch (kind) {
                    case THEME -> "/assessment/themes";
                    case SEMESTER -> "/assessment/semester";
                    case YEAR -> "/assessment/year-end";
                };
        return page + "?classId=" + classId + "&period=" + periodKey;
    }

    static String weekly(Long classId, java.time.LocalDate weekStart) {
        return "/assessment/weekly-comments?classId=" + classId + "&weekStart=" + weekStart;
    }

    static String daily(Long classId, java.time.LocalDate date) {
        return "/assessment/daily?classId=" + classId + "&date=" + date;
    }

    /** Thêm khóa lời nhắc vào đường dẫn: cùng người + loại + khóa chỉ nhận một lần (notifyUsers once). */
    static String once(String link, String reminderKey) {
        return link + (link.contains("?") ? "&" : "?") + "r=" + reminderKey;
    }
}
