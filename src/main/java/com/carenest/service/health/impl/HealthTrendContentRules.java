package com.carenest.service.health.impl;

import com.carenest.dto.health.HealthTrendContent;
import com.carenest.dto.health.HealthTrendContent.AttentionPoint;
import com.carenest.dto.health.HealthTrendContent.FollowUp;
import com.carenest.dto.health.PublishedHealthResponse.TrendWarning;
import com.carenest.utils.enums.HealthTrendFollowUpType;
import com.carenest.utils.enums.HealthTrendWarningCode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kiểm và chuẩn hóa giải thích xu hướng do AI viết (HLT-21, HLT-04): AI chỉ viết lời, còn cảnh báo nào được nhắc và loại
 * gợi ý nào được dùng do hệ thống quyết định. Mục vi phạm bị bỏ; chỉ bỏ cả bản khi tổng quan hỏng.
 */
final class HealthTrendContentRules {
    static final int MAX_OVERVIEW = 500;
    static final int MAX_ITEM = 300;
    static final int MAX_POSITIVES = 3;
    // HLT-04: AI không chẩn đoán, không khuyên điều trị
    private static final List<String> FORBIDDEN = List.of(
            "chẩn đoán",
            "mắc bệnh",
            "bị bệnh",
            "căn bệnh",
            "rối loạn",
            "điều trị",
            "dùng thuốc",
            "uống thuốc",
            "kê đơn",
            "toa thuốc",
            "bệnh lý");
    private static final Pattern FENCE = Pattern.compile("^```[a-zA-Z]*\\s*(.*?)\\s*```$", Pattern.DOTALL);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HealthTrendContentRules() {}

    /** Đọc JSON của AI; rỗng khi JSON hỏng, thiếu tổng quan hoặc tổng quan có ngôn ngữ chẩn đoán. */
    static Optional<HealthTrendContent> fromAi(
            String raw, List<TrendWarning> warnings, Set<HealthTrendFollowUpType> allowed) {
        JsonNode root;
        try {
            root = JSON.readTree(stripFence(raw));
        } catch (JacksonException ex) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        String overview = overview(text(root.path("overview")));
        if (overview == null) {
            return Optional.empty();
        }

        Map<HealthTrendWarningCode, TrendWarning> warned = new LinkedHashMap<>();
        warnings.stream()
                .filter(w -> w.code() != null && w.code() != HealthTrendWarningCode.NORMAL)
                .forEach(w -> warned.putIfAbsent(w.code(), w));
        Map<HealthTrendWarningCode, String> points = new EnumMap<>(HealthTrendWarningCode.class);
        for (JsonNode node : root.path("attentionPoints")) {
            HealthTrendWarningCode code = enumOf(HealthTrendWarningCode.class, text(node.path("warningCode")));
            String itemText = item(text(node.path("text")));
            if (code != null && warned.containsKey(code) && itemText != null) {
                points.putIfAbsent(code, itemText);
            }
        }
        // AI bỏ sót cảnh báo ⇒ dùng nguyên câu cảnh báo xác định, không để mất cảnh báo
        List<AttentionPoint> attentionPoints = warned.entrySet().stream()
                .map(e -> new AttentionPoint(
                        e.getKey(), points.getOrDefault(e.getKey(), e.getValue().text())))
                .toList();

        List<String> positives = new ArrayList<>();
        for (JsonNode node : root.path("positives")) {
            String itemText = item(text(node));
            if (itemText != null && positives.size() < MAX_POSITIVES) {
                positives.add(itemText);
            }
        }

        Map<HealthTrendFollowUpType, String> followUps = new LinkedHashMap<>();
        for (JsonNode node : root.path("followUps")) {
            HealthTrendFollowUpType type = enumOf(HealthTrendFollowUpType.class, text(node.path("type")));
            String itemText = item(text(node.path("text")));
            if (type != null && allowed.contains(type) && itemText != null) {
                followUps.putIfAbsent(type, itemText);
            }
        }

        return Optional.of(new HealthTrendContent(
                overview,
                attentionPoints,
                List.copyOf(positives),
                followUps.entrySet().stream()
                        .map(e -> new FollowUp(e.getKey(), e.getValue()))
                        .toList(),
                item(text(root.path("limitations")))));
    }

    /** Bản văn ghép từ nội dung có cấu trúc (lưu ở {@code summary}); khối rỗng bị bỏ. */
    static String render(HealthTrendContent c) {
        StringBuilder sb = new StringBuilder("Tổng quan: ").append(c.overview());
        block(
                sb,
                "Cần chú ý",
                c.attentionPoints().stream().map(AttentionPoint::text).toList());
        block(sb, "Điểm tích cực", c.positives());
        block(sb, "Gợi ý theo dõi", c.followUps().stream().map(FollowUp::text).toList());
        if (c.limitations() != null) {
            sb.append("\n\nLưu ý về dữ liệu: ").append(c.limitations());
        }
        return sb.toString();
    }

    static boolean containsForbidden(String text) {
        String lower = text.toLowerCase(Locale.forLanguageTag("vi"));
        return FORBIDDEN.stream().anyMatch(lower::contains);
    }

    private static void block(StringBuilder sb, String heading, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        sb.append("\n\n").append(heading).append(':');
        items.forEach(i -> sb.append("\n- ").append(i));
    }

    /** Tổng quan quá dài ⇒ cắt tại dấu kết câu cuối cùng trong giới hạn; không cắt được ⇒ null. */
    private static String overview(String value) {
        if (value == null || value.isBlank() || containsForbidden(value)) {
            return null;
        }
        String v = value.strip();
        if (v.length() <= MAX_OVERVIEW) {
            return v;
        }
        String head = v.substring(0, MAX_OVERVIEW);
        int end = Math.max(head.lastIndexOf('.'), Math.max(head.lastIndexOf('!'), head.lastIndexOf('?')));
        return end < 0 ? null : head.substring(0, end + 1);
    }

    private static String item(String value) {
        if (value == null || value.isBlank() || containsForbidden(value)) {
            return null;
        }
        String v = value.strip();
        return v.length() > MAX_ITEM ? null : v;
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || node.isObject() || node.isArray()
                ? null
                : node.asText();
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String stripFence(String raw) {
        String v = raw == null ? "" : raw.strip();
        Matcher m = FENCE.matcher(v);
        return m.matches() ? m.group(1) : v;
    }
}
