package com.carenest.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Nội dung nháp LLM trả về, đã qua AiOutputGuard. Lưu nguyên vào child_summaries.ai_draft. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SummaryDraftContent(
        String summary, List<EvidencedPoint> strengths, List<EvidencedPoint> areasForFollowUp, List<Long> sources) {

    /** evidenceIds là các ref có trong input: "NOTE-<id>" hoặc "CRITERIA-<code>". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvidencedPoint(String text, List<String> evidenceIds) {}
}
