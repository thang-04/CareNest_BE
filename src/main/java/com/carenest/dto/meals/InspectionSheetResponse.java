package com.carenest.dto.meals;

import com.carenest.utils.enums.InspectionStatus;
import com.carenest.utils.enums.InspectionTab;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Một biên bản kiểm thực của điểm trường–ngày (INS-01). {@code saved=false}: chưa lưu lần nào, dòng là khung dựng từ dữ
 * liệu nguồn. Đã ký ({@code SIGNED}) thì {@code rows} là bản chụp lúc ký (INS-03). Ảnh chữ ký tải riêng qua
 * {@code GET /inspections/signatures/{imageKey}}.
 */
public record InspectionSheetResponse(
        InspectionTab tab,
        InspectionStatus status,
        int version,
        boolean saved,
        Map<String, Object> header,
        List<Map<String, Object>> rows,
        Signature signature,
        List<Event> history,
        Instant updatedAt) {

    public record Signature(
            Long signedBy, String signedByName, Instant signedAt, int documentVersion, String imageKey) {}

    public record Event(String action, String actorName, Instant at, String note, String imageKey) {}
}
