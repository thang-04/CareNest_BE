package com.carenest.service.observation.impl;

/**
 * Hạn cố định (Cần chốt 14/11/2026, không có cấu hình): số ngày học sau ngày kết thúc kỳ trước khi khóa lúc 23:59:59.
 * Ngoại lệ thì BGH mở lại theo lớp – kỳ.
 */
final class AssessmentDeadlines {

    /** Nhận xét tuần: sau ngày học cuối của tuần giáo án (OBS-29). */
    static final int WEEKLY_COMMENT = 1;

    /** Đánh giá cuối chủ đề: sau ngày kết thúc chủ đề (OBS-27). */
    static final int THEME = 3;

    /** Đánh giá học kỳ: sau ngày kết thúc học kỳ (OBS-06). */
    static final int SEMESTER = 5;

    /** Đánh giá cuối năm: sau ngày kết thúc HK II; có thể sau ngày kết thúc năm học, HT đóng năm thì khóa (OBS-28). */
    static final int YEAR_END = 5;

    private AssessmentDeadlines() {}
}
