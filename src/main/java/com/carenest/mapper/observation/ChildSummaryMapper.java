package com.carenest.mapper.observation;

import com.carenest.dto.observation.ChildSummaryResponse;
import com.carenest.dto.observation.ChildSummaryResponse.AiDraftView;
import com.carenest.dto.observation.ChildSummaryResponse.PointView;
import com.carenest.entity.observation.ChildSummary;
import com.carenest.integration.ai.dto.SummaryDraftContent;
import com.carenest.integration.ai.dto.SummaryDraftContent.EvidencedPoint;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ChildSummaryMapper {

    private final ObjectMapper objectMapper;

    public ChildSummaryResponse toResponse(ChildSummary summary, String aiErrorCode) {
        return new ChildSummaryResponse(
                summary.getId(),
                summary.getChildId(),
                summary.getPeriodType(),
                summary.getPeriodFrom(),
                summary.getPeriodTo(),
                summary.getSource(),
                summary.getStatus(),
                summary.getContent(),
                toDraftView(summary.getAiDraft()),
                summary.getAiModel(),
                summary.getAiPromptVersion(),
                summary.getGeneratedAt(),
                summary.getApprovedBy(),
                summary.getApprovedAt(),
                aiErrorCode);
    }

    private AiDraftView toDraftView(String aiDraft) {
        if (aiDraft == null) {
            return null;
        }
        SummaryDraftContent draft = objectMapper.readValue(aiDraft, SummaryDraftContent.class);
        return new AiDraftView(
                draft.summary(), toPoints(draft.strengths()), toPoints(draft.areasForFollowUp()), draft.sources());
    }

    private static List<PointView> toPoints(List<EvidencedPoint> points) {
        return points == null
                ? List.of()
                : points.stream()
                        .map(point -> new PointView(point.text(), point.evidenceIds()))
                        .toList();
    }
}
