package com.carenest.dto.health;

import java.time.Instant;
import java.util.List;

/**
 * Phân tích xu hướng sức khỏe như BE giả của FE ({@code POST /children/{id}/health/trend-analysis}): {@code status}
 * {@code OK} | {@code NOT_ENOUGH_DATA} (dưới 2 lần đo — không gọi AI, không lưu). {@code draft} true tới khi nhân sự duyệt
 * (HLT-05); {@code warnings} tính xác định (HLT-02); {@code content} có cấu trúc do AI viết (HLT-21, null ở bản trước V129),
 * {@code summary} là bản văn ghép từ {@code content}. Không chứa dữ liệu nhận diện trẻ.
 */
public record HealthTrendAnalysisResponse(
        Long id,
        String status,
        boolean draft,
        Instant generatedAt,
        String summary,
        HealthTrendContent content,
        List<PublishedHealthResponse.TrendWarning> warnings,
        String source,
        String model,
        Long approvedBy,
        String approvedByName,
        Instant approvedAt) {

    public static final String OK = "OK";
    public static final String NOT_ENOUGH_DATA = "NOT_ENOUGH_DATA";

    public static HealthTrendAnalysisResponse notEnoughData() {
        return new HealthTrendAnalysisResponse(
                null, NOT_ENOUGH_DATA, false, null, "", null, List.of(), null, null, null, null, null);
    }
}
