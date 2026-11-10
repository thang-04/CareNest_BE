package com.carenest.dto.observation;

import com.carenest.utils.enums.SummaryPeriodType;
import com.carenest.utils.enums.SummarySource;
import com.carenest.utils.enums.SummaryStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * aiDraft: bản AI soạn gốc (kèm evidence) để GV đối chiếu, null khi source = TEMPLATE. aiErrorCode chỉ có trong
 * response tạo nháp khi AI không dùng được (vd. AI_DISABLED, AI_TIMEOUT); GV vẫn nhận bản template để sửa.
 */
public record ChildSummaryResponse(
        Long id,
        Long childId,
        SummaryPeriodType periodType,
        LocalDate periodFrom,
        LocalDate periodTo,
        SummarySource source,
        SummaryStatus status,
        String content,
        AiDraftView aiDraft,
        String aiModel,
        String aiPromptVersion,
        Instant generatedAt,
        Long approvedBy,
        Instant approvedAt,
        String aiErrorCode) {

    public record AiDraftView(
            String summary, List<PointView> strengths, List<PointView> areasForFollowUp, List<Long> sources) {}

    public record PointView(String text, List<String> evidenceIds) {}
}
