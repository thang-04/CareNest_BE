package com.carenest.service.observation.impl;

import com.carenest.integration.ai.dto.SummaryDraftContent;
import com.carenest.integration.ai.dto.SummaryDraftContent.EvidencedPoint;
import com.carenest.integration.ai.dto.SummaryDraftInput.CriteriaFact;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Dựng nội dung nhận xét cho GV sửa: từ nháp AI, hoặc template thuần code khi AI tắt/lỗi (OBS-04). */
final class SummaryContentRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // Model đôi khi chèn mã evidence vào câu, vd. "(NOTE-17)"; mã đã nằm ở aiDraft.evidenceIds, không để GV thấy
    private static final Pattern EVIDENCE_CODE = Pattern.compile(
            "\\s*[(\\[]?\\b(?:NOTE|CRITERIA)-[A-Za-z0-9_-]+(?:\\s*,\\s*(?:NOTE|CRITERIA)-[A-Za-z0-9_-]+)*[)\\]]?");

    private SummaryContentRenderer() {}

    static String fromDraft(SummaryDraftContent draft) {
        StringBuilder content = new StringBuilder(withoutEvidenceCodes(draft.summary()));
        appendPoints(content, "Điểm mạnh", draft.strengths());
        appendPoints(content, "Cần tiếp tục quan sát", draft.areasForFollowUp());
        return content.toString();
    }

    static String fromCounts(LocalDate from, LocalDate to, int observedDays, List<CriteriaFact> criteria) {
        StringBuilder content = new StringBuilder()
                .append("Từ ")
                .append(DATE.format(from))
                .append(" đến ")
                .append(DATE.format(to))
                .append(", giáo viên ghi nhận ")
                .append(observedDays)
                .append(" ngày quan sát.");
        for (CriteriaFact fact : criteria) {
            String counts = fact.optionCounts().entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(entry -> entry.getKey() + " " + entry.getValue() + " ngày")
                    .collect(Collectors.joining(", "));
            content.append("\n- ")
                    .append(fact.name())
                    .append(": ")
                    .append(counts)
                    .append('.');
        }
        return content.append("\n\n(Giáo viên bổ sung nhận xét.)").toString();
    }

    private static void appendPoints(StringBuilder content, String heading, List<EvidencedPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        content.append("\n\n").append(heading).append(':');
        points.forEach(point -> content.append("\n- ").append(withoutEvidenceCodes(point.text())));
    }

    static String withoutEvidenceCodes(String text) {
        return EVIDENCE_CODE
                .matcher(text)
                .replaceAll("")
                .replaceAll(" +([.,;:])", "$1")
                .strip();
    }
}
