package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.EduConfigResponse.WeekRow;
import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Thẻ hoạt động của kế hoạch chủ đề (EDU-78): {id, slot, name, goalIds, contentRefs, legacy, origin}. Dữ liệu cũ dạng ô chữ
 * "Loại giờ: …" chuẩn hóa khi đọc, giữ id "goalId#i" để picks và học liệu cũ vẫn trỏ đúng (research R2).
 */
final class EduActivityCards {
    static final int NAME_MAX = 300;
    private static final Pattern CONTENT_REF = Pattern.compile("(\\d+):(\\d+)");
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
    // Cùng bảng tên loại giờ của Web (classPlan.js SLOT_WORDS), giữ đúng thứ tự ưu tiên
    private static final List<Map.Entry<String, Pattern>> SLOT_WORDS = List.of(
            Map.entry("don", Pattern.compile("đón trẻ", FLAGS)),
            Map.entry("tro_chuyen", Pattern.compile("trò chuyện", FLAGS)),
            Map.entry("the_duc", Pattern.compile("thể dục sáng|\\btds\\b|\\btd sáng", FLAGS)),
            Map.entry("hoc", Pattern.compile("hđ học|hoạt động học|giờ học|\\bhđh\\b|hđ tạo hình", FLAGS)),
            Map.entry("ngoai_troi", Pattern.compile("ngoài trời", FLAGS)),
            Map.entry("goc", Pattern.compile("góc|hđ chơi\\b|hoạt động chơi\\b", FLAGS)),
            Map.entry("chieu", Pattern.compile("chiều|theo ý thích|lđvs|lao động|nêu gương", FLAGS)),
            Map.entry("tra_tre", Pattern.compile("trả trẻ", FLAGS)));
    private static final Pattern BULLET = Pattern.compile("^[\\s\\-–•+*]+");
    private static final Pattern SUB = Pattern.compile("^\\s*\\+");

    private EduActivityCards() {}

    /**
     * Dựng {@code activities} từ {@code rows[goalId].activities} khi bản lưu chưa có thẻ. goalIds = null thì lấy mọi dòng;
     * ngược lại chỉ MT của chủ đề (như themeActivities của Web chỉ đọc MT đang hiện). Đã có {@code activities} thì trả nguyên.
     */
    static ArrayNode normalizeTheme(ObjectNode content, List<WeekRow> rows, Set<Long> goalIds) {
        if (content.path("activities") instanceof ArrayNode existing) return existing;
        ArrayNode cards = JsonNodeFactory.instance.arrayNode();
        Set<String> slotKeys = new HashSet<>();
        rows.forEach(r -> slotKeys.add(r.key()));
        content.path("rows").properties().forEach(e -> {
            Long goalId = parseLong(e.getKey());
            if (goalId == null || goalIds != null && !goalIds.contains(goalId)) return;
            String[] heading = null;
            int n = 0;
            for (String raw : e.getValue().path("activities").asString("").split("\n", -1)) {
                boolean sub = SUB.matcher(raw).find();
                String line = BULLET.matcher(raw).replaceFirst("").trim();
                if (line.isEmpty()) continue;
                int colon = line.indexOf(':');
                String head = colon >= 0 ? line.substring(0, colon) : line;
                // Đầu mục rỗng ("HĐ học:") nhớ loại giờ cho các dòng con "+ …"
                if (!sub && colon >= 0 && line.substring(colon + 1).isBlank()) {
                    heading = new String[] {head.trim(), slotOf(head, rows, slotKeys)};
                    continue;
                }
                if (!sub) heading = null;
                String own = slotOf(head, rows, slotKeys);
                boolean inherit = sub && heading != null && own.isEmpty();
                ObjectNode card = cards.addObject();
                card.put("id", goalId + "#" + n++);
                card.put("slot", inherit ? heading[1] : own);
                card.put("name", inherit ? heading[0] + ": " + line : line);
                card.putArray("goalIds").add(goalId);
                card.putArray("contentRefs");
                card.put("legacy", true);
            }
        });
        return cards;
    }

    /** Loại giờ của đầu dòng: khớp đúng tên dòng tuần trước, rồi tới tên quen dùng; chỉ nhận dòng đang có trong cấu hình. */
    static String slotOf(String head, List<WeekRow> rows, Set<String> slotKeys) {
        String h = head.trim().toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        if (!h.isEmpty())
            rows.stream()
                    .filter(r -> r.label().trim().toLowerCase(Locale.ROOT).equals(h))
                    .forEach(r -> found.add(r.key()));
        SLOT_WORDS.stream()
                .filter(w -> w.getValue().matcher(head).find())
                .forEach(w -> found.add(w.getKey()));
        return found.stream().filter(slotKeys::contains).findFirst().orElse("");
    }

    /** Thẻ gắn MT đã rời chủ đề (vẫn là MT của năm): bỏ MT đó và nội dung của nó, giữ thẻ (edge case "thẻ mất MT"). */
    static void dropGoalsOutside(ArrayNode cards, Set<Long> themeGoalIds, Set<Long> planGoalIds) {
        for (JsonNode card : cards) {
            if (!(card instanceof ObjectNode c) || !(c.path("goalIds") instanceof ArrayNode ids)) continue;
            Set<Long> gone = new HashSet<>();
            for (int i = ids.size() - 1; i >= 0; i--) {
                long id = longOf(ids.get(i));
                if (!themeGoalIds.contains(id) && planGoalIds.contains(id)) {
                    gone.add(id);
                    ids.remove(i);
                }
            }
            if (!gone.isEmpty() && c.path("contentRefs") instanceof ArrayNode refs)
                for (int i = refs.size() - 1; i >= 0; i--) {
                    var m = CONTENT_REF.matcher(refs.get(i).asString(""));
                    if (m.matches() && gone.contains(Long.valueOf(m.group(1)))) refs.remove(i);
                }
        }
    }

    /** R8: id duy nhất, tên 1–300 ký tự, loại giờ thuộc cấu hình ("" chỉ với thẻ cũ), MT thuộc chủ đề, nội dung thuộc MT đã chọn. */
    static void validate(JsonNode cards, Set<Long> themeGoalIds, Set<String> slotKeys) {
        if (cards == null || cards.isMissingNode() || cards.isNull()) return;
        if (!cards.isArray()) throw invalid("thẻ hoạt động");
        Set<String> ids = new HashSet<>();
        for (JsonNode card : cards) {
            if (!card.isObject()) throw invalid("thẻ hoạt động");
            String id = card.path("id").asString("").trim();
            if (id.isEmpty() || id.length() > 100 || !ids.add(id)) throw invalid("mã thẻ hoạt động");
            String name = card.path("name").asString("").trim();
            if (name.isEmpty()) throw invalid("tên hoạt động");
            if (name.length() > NAME_MAX) throw invalid("tên hoạt động (tối đa " + NAME_MAX + " ký tự)");
            String slot = card.path("slot").asString("");
            if (slot.isEmpty() ? !card.path("legacy").asBoolean(false) : !slotKeys.contains(slot))
                throw invalid("loại giờ của hoạt động \"" + name + "\"");
            JsonNode goals = card.path("goalIds");
            if (!goals.isArray() && !goals.isMissingNode()) throw invalid("mục tiêu của hoạt động \"" + name + "\"");
            Set<Long> own = new HashSet<>();
            for (JsonNode g : goals) {
                long goalId = longOf(g);
                if (!themeGoalIds.contains(goalId)) throw invalid("mục tiêu của hoạt động \"" + name + "\"");
                own.add(goalId);
            }
            JsonNode refs = card.path("contentRefs");
            if (!refs.isArray() && !refs.isMissingNode()) throw invalid("nội dung của hoạt động \"" + name + "\"");
            for (JsonNode ref : refs) {
                var m = CONTENT_REF.matcher(ref.asString(""));
                if (!m.matches() || !own.contains(Long.valueOf(m.group(1))))
                    throw invalid("nội dung của hoạt động \"" + name + "\"");
            }
            String origin = card.path("origin").asString("theme");
            if (!Set.of("theme", "dossier").contains(origin)) throw invalid("thẻ hoạt động");
        }
    }

    /** Thẻ dùng để giải MT: thẻ của kế hoạch chủ đề cộng thẻ bổ sung theo hồ sơ chủ đề ({@code day.content.week.cards}). */
    static Map<String, JsonNode> cards(JsonNode themeCards, JsonNode dayWeek) {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        if (themeCards != null) themeCards.forEach(c -> byId.put(c.path("id").asString(""), c));
        if (dayWeek != null) dayWeek.path("cards").forEach(c -> byId.putIfAbsent(c.path("id").asString(""), c));
        return byId;
    }

    /** R3: MT của một lượt xếp = MT của thẻ; không thấy thẻ thì dùng bản chụp goalIds, bản cũ thì goalId. Gõ tay ⇒ rỗng. */
    static List<Long> resolveGoals(JsonNode pick, Map<String, JsonNode> cardsById) {
        String id = pick.path("cardId").asString("");
        if (id.isEmpty()) id = pick.path("id").asString("");
        JsonNode card = cardsById.get(id);
        JsonNode goals = card != null ? card.path("goalIds") : pick.path("goalIds");
        Set<Long> out = new LinkedHashSet<>();
        if (goals.isArray() && (card != null || !goals.isEmpty())) {
            goals.forEach(g -> {
                long v = longOf(g);
                if (v > 0) out.add(v);
            });
            return List.copyOf(out);
        }
        Long legacy = parseLong(pick.path("goalId").asString(""));
        return legacy == null ? List.of() : List.of(legacy);
    }

    /** Web gửi id MT dạng chuỗi ("12") hoặc số. */
    static long longOf(JsonNode node) {
        if (node.isIntegralNumber()) return node.longValue();
        Long v = parseLong(node.asString(""));
        return v == null ? -1 : v;
    }

    private static Long parseLong(String raw) {
        return raw != null && raw.trim().matches("\\d+") ? Long.valueOf(raw.trim()) : null;
    }

    private static GlobalException invalid(String label) {
        return GlobalException.of(ApiCode.INVALID_VALUE, label);
    }
}
