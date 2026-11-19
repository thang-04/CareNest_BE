package com.carenest.dto.health;

import com.carenest.utils.enums.HealthTrendFollowUpType;
import com.carenest.utils.enums.HealthTrendWarningCode;
import java.util.List;

/**
 * Giải thích xu hướng sức khỏe có cấu trúc (HLT-21): AI viết nháp, nhân sự duyệt (HLT-05). {@code followUps} là việc nội
 * bộ của trường — phụ huynh nhận {@link #forParent()}.
 */
public record HealthTrendContent(
        String overview,
        List<AttentionPoint> attentionPoints,
        List<String> positives,
        List<FollowUp> followUps,
        String limitations) {

    public record AttentionPoint(HealthTrendWarningCode warningCode, String text) {}

    public record FollowUp(HealthTrendFollowUpType type, String text) {}

    public HealthTrendContent forParent() {
        return new HealthTrendContent(overview, attentionPoints, positives, List.of(), limitations);
    }
}
