package com.carenest.service.health.impl;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.HealthTrendContent;
import com.carenest.dto.health.HealthTrendContent.AttentionPoint;
import com.carenest.dto.health.HealthTrendContent.FollowUp;
import com.carenest.dto.health.PublishedHealthResponse.TrendWarning;
import com.carenest.utils.enums.HealthTrendFollowUpType;
import com.carenest.utils.enums.HealthTrendWarningCode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

    /** Nội dung nhân sự gửi khi duyệt: bỏ khoảng trắng thừa, danh sách null ⇒ rỗng, câu trống ⇒ null. */
    static HealthTrendContent normalize(HealthTrendContent c) {
        return new HealthTrendContent(
                trim(c.overview()),
                c.attentionPoints() == null
                        ? List.of()
                        : c.attentionPoints().stream()
                                .map(a -> new AttentionPoint(a.warningCode(), trim(a.text())))
                                .toList(),
                c.positives() == null
                        ? List.of()
                        : c.positives().stream()
                                .map(HealthTrendContentRules::trim)
                                .toList(),
                c.followUps() == null
                        ? List.of()
                        : c.followUps().stream()
                                .map(f -> new FollowUp(f.type(), trim(f.text())))
                                .toList(),
                trim(c.limitations()));
    }

    /**
     * Kiểm bản nhân sự sửa (AI-02, HLT-21): cùng giới hạn độ dài như bản AI; điểm cần chú ý chỉ gắn mã có trong cảnh báo
     * của lần phân tích, mỗi mã một mục; mỗi loại gợi ý một mục. Không kiểm từ cấm — nhân sự chịu trách nhiệm nội dung.
     */
    static List<FieldErrorResponse> checkEdited(HealthTrendContent c, List<TrendWarning> warnings) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (c.overview() == null) {
            errors.add(new FieldErrorResponse("content.overview", "Tổng quan không được để trống"));
        } else if (c.overview().length() > MAX_OVERVIEW) {
            errors.add(new FieldErrorResponse("content.overview", "Tổng quan tối đa " + MAX_OVERVIEW + " ký tự"));
        }
        Set<HealthTrendWarningCode> warned = warnings.stream()
                .map(TrendWarning::code)
                .filter(code -> code != null && code != HealthTrendWarningCode.NORMAL)
                .collect(Collectors.toSet());
        Set<HealthTrendWarningCode> seenCodes = EnumSet.noneOf(HealthTrendWarningCode.class);
        for (int i = 0; i < c.attentionPoints().size(); i++) {
            AttentionPoint a = c.attentionPoints().get(i);
            String field = "content.attentionPoints[" + i + "]";
            if (a.warningCode() == null || !warned.contains(a.warningCode()) || !seenCodes.add(a.warningCode())) {
                errors.add(new FieldErrorResponse(field + ".warningCode", "Mã cảnh báo không có trong lần phân tích"));
            }
            checkItem(errors, field + ".text", a.text());
        }
        if (c.positives().size() > MAX_POSITIVES) {
            errors.add(new FieldErrorResponse("content.positives", "Tối đa " + MAX_POSITIVES + " điểm tích cực"));
        }
        for (int i = 0; i < c.positives().size(); i++) {
            checkItem(errors, "content.positives[" + i + "]", c.positives().get(i));
        }
        Set<HealthTrendFollowUpType> seenTypes = EnumSet.noneOf(HealthTrendFollowUpType.class);
        for (int i = 0; i < c.followUps().size(); i++) {
            FollowUp f = c.followUps().get(i);
            String field = "content.followUps[" + i + "]";
            if (f.type() == null || !seenTypes.add(f.type())) {
                errors.add(new FieldErrorResponse(field + ".type", "Loại gợi ý trống hoặc bị trùng"));
            }
            checkItem(errors, field + ".text", f.text());
        }
        if (c.limitations() != null && c.limitations().length() > MAX_ITEM) {
            errors.add(new FieldErrorResponse("content.limitations", "Tối đa " + MAX_ITEM + " ký tự"));
        }
        return errors;
    }

    private static void checkItem(List<FieldErrorResponse> errors, String field, String text) {
        if (text == null) {
            errors.add(new FieldErrorResponse(field, "Không được để trống"));
        } else if (text.length() > MAX_ITEM) {
            errors.add(new FieldErrorResponse(field, "Tối đa " + MAX_ITEM + " ký tự"));
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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
