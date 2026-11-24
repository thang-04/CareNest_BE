package com.carenest.service.observation.impl;

import com.carenest.dto.observation.EvaluationDtos.StructuredSummary;
import com.carenest.utils.enums.EvaluationPeriodType;
import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu một trẻ trong một kỳ để AI viết nháp nhận xét (OBS-34). Họ tên và ngày sinh chỉ dùng để lọc nhận xét tự
 * do và ghép tên vào bản nháp, không đi vào prompt (AI-04).
 *
 * @param finals nhận xét kỳ nhỏ hơn GV đã xác nhận (học kỳ: các tháng; cuối năm: các học kỳ)
 * @param weekly nhận xét tuần đã gửi phụ huynh trong kỳ
 * @param daily nhận xét hằng ngày của GV trong kỳ
 */
record PeriodCommentFacts(
        EvaluationPeriodType type,
        LocalDate start,
        LocalDate end,
        String childFullName,
        LocalDate dateOfBirth,
        int presentDays,
        int flagCount,
        StructuredSummary structured,
        List<Note> finals,
        List<Note> weekly,
        List<Note> daily) {

    /** Một nhận xét của GV; label là nhãn thời điểm do code đặt (vd. "Ngày 12/10"). */
    record Note(String label, String text) {}

    /** Có gì để AI tổng hợp: ngày đã đánh giá hoặc nhận xét của GV. */
    boolean hasContent() {
        return structured.observedDays() > 0 || !finals.isEmpty() || !weekly.isEmpty() || !daily.isEmpty();
    }
}
