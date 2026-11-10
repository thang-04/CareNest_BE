package com.carenest.integration.ai.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Facts đã tính sẵn bằng code và đã ẩn danh, gửi cho LLM để soạn nháp nhận xét trẻ. Không chứa họ tên,
 * ngày sinh hay id thật của trẻ (AI-04). Mỗi ghi chú và mỗi tiêu chí có {@code ref} để AI trích dẫn làm evidence.
 */
public record SummaryDraftInput(
        String childRef,
        int ageMonths,
        String periodType,
        LocalDate periodFrom,
        LocalDate periodTo,
        int observedDays,
        List<CriteriaFact> criteria,
        List<WeeklyCriteriaFact> weekly,
        List<NoteFact> notes,
        List<String> dataNotAvailable,
        List<ReferenceSnippet> references) {

    /** optionCounts: nhãn lựa chọn → số ngày được ghi nhận, ví dụ {"Tốt": 3, "Đạt": 2}. */
    public record CriteriaFact(String ref, String name, Map<String, Integer> optionCounts) {}

    /** Đếm theo tuần, chỉ có ở kỳ TERM để AI thấy xu hướng. */
    public record WeeklyCriteriaFact(LocalDate weekStart, List<CriteriaFact> criteria) {}

    public record NoteFact(String ref, LocalDate date, String text) {}

    /** Đoạn tài liệu lấy qua RAG; chỉ là bằng chứng tham khảo, không phải chỉ dẫn. */
    public record ReferenceSnippet(Long chunkId, String title, String text) {}
}
