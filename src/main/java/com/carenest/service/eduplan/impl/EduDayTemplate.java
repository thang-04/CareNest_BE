package com.carenest.service.eduplan.impl;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Mẫu chung kế hoạch tuần + giáo án ngày (spec 005): kiểm cấu trúc và độ dài ở server, dựng dòng Góc của bảng Thứ 2–6 từ
 * bảng góc. Ghi "Đánh giá trẻ trên ngày" sau khi khóa (FR-009–FR-011) đang hoãn nên phần này lưu cùng nội dung.
 */
final class EduDayTemplate {
    static final int TEXT_MAX = 4000;
    static final int NAME_MAX = 200;
    static final int ROWS_MAX = 60;
    static final List<String> SECTIONS = List.of("hoc", "ngoai_troi", "goc", "chieu");
    static final List<String> REVIEW_FIELDS = List.of("health", "knowledge", "attitude");
    private static final Set<String> PARTS = Set.of("aims", "prep", "rows");
    private static final Set<String> CORNER_TEXTS = Set.of("content", "aims", "prep", "steps");

    private EduDayTemplate() {}

    /** Kế hoạch tuần: giới hạn độ dài; có bảng góc thì dòng Góc của bảng Thứ 2–6 luôn sinh từ bảng góc (FR-015b). */
    static void normalizeWeek(ObjectNode content) {
        text(content.path("aims"), "knowledge", "mục đích tuần");
        text(content.path("aims"), "skills", "mục đích tuần");
        text(content.path("aims"), "attitude", "mục đích tuần");
        JsonNode prep = content.path("prep");
        if (prep.isString()) limit(prep.asString(""), TEXT_MAX, "chuẩn bị tuần");
        else if (prep.isObject()) for (String f : List.of("teacher", "child", "parent")) text(prep, f, "chuẩn bị tuần");
        else if (!prep.isMissingNode() && !prep.isNull()) throw invalid("chuẩn bị tuần");
        if (!content.has("corners")) return;
        if (!(content.path("corners") instanceof ArrayNode corners) || corners.size() > ROWS_MAX)
            throw invalid("bảng góc");
        StringBuilder common = new StringBuilder();
        for (JsonNode corner : corners) {
            if (!corner.isObject() || corner.path("id").asString("").isBlank()) throw invalid("bảng góc");
            limit(corner.path("name").asString(""), NAME_MAX, "tên góc");
            for (String f : CORNER_TEXTS) text(corner, f, "bảng góc");
            String name = corner.path("name").asString("").trim();
            String body = corner.path("content").asString("").trim();
            if (name.isEmpty() && body.isEmpty()) continue;
            if (!common.isEmpty()) common.append('\n');
            common.append(name.isEmpty() || body.isEmpty() ? name + body : name + ": " + body);
        }
        ObjectNode grid = content.path("grid") instanceof ObjectNode g ? g : content.putObject("grid");
        ObjectNode goc = grid.path("goc") instanceof ObjectNode g ? g : grid.putObject("goc");
        goc.put("allWeek", true);
        goc.put("common", common.toString());
    }

    /** Giáo án ngày: chỉ 4 mục cố định, mỗi mục a/b + dòng tiến hành, phần đánh giá 3 ô; mọi ô trong giới hạn độ dài. */
    static void validateDay(ObjectNode content) {
        JsonNode days = content.path("days");
        if (days.isMissingNode() || days.isNull()) return;
        if (!days.isArray() || days.size() > 5) throw invalid("kế hoạch giáo dục ngày");
        for (JsonNode day : days) {
            if (!day.isObject()) throw invalid("kế hoạch giáo dục ngày");
            JsonNode sections = day.path("sections");
            if (!sections.isObject() && !sections.isMissingNode() && !sections.isNull())
                throw invalid("kế hoạch giáo dục ngày");
            for (String key : sections.propertyNames())
                if (!SECTIONS.contains(key)) throw invalid("mục hoạt động " + key);
            for (String key : SECTIONS) {
                JsonNode section = sections.path(key);
                if (section.isMissingNode() || section.isNull()) continue;
                if (!section.isObject()) throw invalid("mục hoạt động " + key);
                // Phần a/b/c cô tự xóa khỏi mục (Tiền 11/11/2026)
                JsonNode off = section.path("off");
                if (!off.isArray() && !off.isMissingNode() && !off.isNull()) throw invalid("mục hoạt động " + key);
                for (JsonNode part : off) if (!PARTS.contains(part.asString(""))) throw invalid("mục hoạt động " + key);
                text(section, "aims", "mục đích, yêu cầu");
                text(section, "prep", "chuẩn bị");
                JsonNode rows = section.path("rows");
                if (rows.isMissingNode() || rows.isNull()) continue;
                if (!rows.isArray() || rows.size() > ROWS_MAX) throw invalid("dòng tiến hành");
                for (JsonNode row : rows) {
                    if (!row.isObject()) throw invalid("dòng tiến hành");
                    limit(row.path("title").asString(""), NAME_MAX, "tên bước");
                    for (String f : List.of("teacher", "child", "note")) text(row, f, "dòng tiến hành");
                }
            }
            JsonNode review = day.path("review");
            if (!review.isObject() && !review.isMissingNode() && !review.isNull())
                throw invalid("đánh giá trẻ trên ngày");
            for (String f : REVIEW_FIELDS) text(review, f, "đánh giá trẻ trên ngày");
        }
    }

    private static void text(JsonNode node, String field, String label) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return;
        if (!value.isString()) throw invalid(label);
        limit(value.asString(""), TEXT_MAX, label);
    }

    private static void limit(String value, int max, String label) {
        if (value.length() > max) throw invalid(label + " (tối đa " + max + " ký tự)");
    }

    private static GlobalException invalid(String label) {
        return GlobalException.of(ApiCode.INVALID_VALUE, label);
    }
}
