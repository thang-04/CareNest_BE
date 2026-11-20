package com.carenest.service.meals.impl;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bước 5 và 9: guard cấu trúc output model (AI-01, NUT-05) rồi map bí danh về món thật. Output chỉ được dùng khi đúng
 * dạng, đủ ngày yêu cầu, mỗi ngày đủ mọi ô, mỗi ô một bí danh thuộc ứng viên của chính ô đó, không lặp món trong bữa.
 * Lý do của model bị xóa chữ số, bỏ câu có thuật ngữ y khoa, cắt độ dài: số liệu hiển thị chỉ lấy từ evaluator.
 */
final class MenuAiOutputParser {

    static final int REASON_MAX = 150;
    private static final Pattern FENCE = Pattern.compile("^```[a-zA-Z]*\\s*|\\s*```$");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?;])\\s+|\\n+");
    // AI không chẩn đoán, không khuyên điều trị (nguyên tắc 4, AI-10)
    private static final List<String> MEDICAL_TERMS = List.of(
            "bệnh",
            "chẩn đoán",
            "điều trị",
            "chữa",
            "thuốc",
            "triệu chứng",
            "suy dinh dưỡng",
            "béo phì",
            "thừa cân",
            "còi xương",
            "thiếu máu",
            "tiểu đường",
            "kháng sinh",
            "vắc xin");

    /** {@code error} null ⇔ hợp lệ; khi lỗi, {@code grid} rỗng và {@code error} là mô tả gọn để gửi lại model. */
    record Parsed(MenuGrid grid, String error) {
        boolean valid() {
            return error == null;
        }

        static Parsed invalid(String error) {
            return new Parsed(MenuGrid.empty(), error);
        }
    }

    /** Một món thay thế: {@code error} null ⇔ hợp lệ. */
    record Choice(Long dishId, String reason, String error) {
        boolean valid() {
            return error == null;
        }
    }

    private MenuAiOutputParser() {}

    /** Thay một món: {@code {"dish":"D3","reason":"..."}}, bí danh phải thuộc ứng viên của ô. */
    static Choice parseChoice(
            String content, MenuAiPromptBuilder.Aliases aliases, List<Long> allowed, ObjectMapper mapper) {
        if (content == null || content.isBlank()) {
            return new Choice(null, "", "trả lời rỗng");
        }
        JsonNode root;
        try {
            root = mapper.readTree(FENCE.matcher(content.trim()).replaceAll(""));
        } catch (JacksonException ex) {
            return new Choice(null, "", "không phải JSON");
        }
        JsonNode dish = root == null ? null : root.get("dish");
        if (dish == null || !dish.isString()) {
            return new Choice(null, "", "thiếu dish");
        }
        Long dishId = aliases.toDish().get(dish.asString().trim().toUpperCase(Locale.ROOT));
        if (dishId == null || !allowed.contains(dishId)) {
            return new Choice(null, "", "món không thuộc danh sách được phép");
        }
        String reason = sanitizeReason(
                root.path("reason").isString() ? root.path("reason").asString() : null);
        return new Choice(dishId, reason, null);
    }

    static Parsed parse(
            String content,
            MenuContext ctx,
            MenuAiPromptBuilder.Aliases aliases,
            Set<LocalDate> expectedDates,
            ObjectMapper mapper) {
        if (content == null || content.isBlank()) {
            return Parsed.invalid("trả lời rỗng");
        }
        JsonNode root;
        try {
            root = mapper.readTree(FENCE.matcher(content.trim()).replaceAll(""));
        } catch (JacksonException ex) {
            return Parsed.invalid("không phải JSON");
        }
        JsonNode days = root == null ? null : root.get("days");
        if (days == null || !days.isArray()) {
            return Parsed.invalid("thiếu mảng days");
        }
        Map<LocalDate, Map<String, Long>> cells = new TreeMap<>();
        Map<LocalDate, String> reasons = new TreeMap<>();
        for (JsonNode day : days) {
            LocalDate date = date(day);
            if (date == null) {
                return Parsed.invalid("ngày sai dạng");
            }
            if (ctx.offDays().contains(date)) {
                if (day.path("items").isArray() && !day.path("items").isEmpty()) {
                    return Parsed.invalid("ngày nghỉ " + date + " phải để trống");
                }
                continue;
            }
            if (!expectedDates.contains(date) || cells.containsKey(date)) {
                return Parsed.invalid("ngày " + date + " không được yêu cầu hoặc bị lặp");
            }
            String error = readDay(day, date, ctx, aliases, cells);
            if (error != null) {
                return Parsed.invalid(error);
            }
            String reason = sanitizeReason(
                    day.path("reason").isString() ? day.path("reason").asString() : null);
            if (!reason.isEmpty()) {
                reasons.put(date, reason);
            }
        }
        if (!cells.keySet().equals(expectedDates)) {
            return Parsed.invalid("thiếu ngày, cần đủ: " + expectedDates);
        }
        return new Parsed(new MenuGrid(cells, reasons), null);
    }

    private static String readDay(
            JsonNode day,
            LocalDate date,
            MenuContext ctx,
            MenuAiPromptBuilder.Aliases aliases,
            Map<LocalDate, Map<String, Long>> cells) {
        JsonNode items = day.get("items");
        if (items == null || !items.isArray()) {
            return "ngày " + date + " thiếu items";
        }
        Map<String, MenuSlot> slots = new LinkedHashMap<>();
        ctx.slots().forEach(s -> slots.put(s.key(), s));
        Map<String, Long> bySlot = new LinkedHashMap<>();
        for (JsonNode item : items) {
            String slotKey =
                    item.path("slot").isString() ? item.path("slot").asString().trim() : "";
            String alias = item.path("dish").isString()
                    ? item.path("dish").asString().trim().toUpperCase(Locale.ROOT)
                    : "";
            MenuSlot slot = slots.get(slotKey);
            if (slot == null) {
                return "ngày " + date + " có ô lạ " + MenuAiPromptBuilder.clean(slotKey, 20);
            }
            Long dishId = aliases.toDish().get(alias);
            if (dishId == null || !ctx.candidatesOf(slot).contains(dishId)) {
                return "ngày " + date + " ô " + slotKey + " dùng món không thuộc danh sách của ô";
            }
            if (bySlot.putIfAbsent(slotKey, dishId) != null) {
                return "ngày " + date + " ô " + slotKey + " điền hai lần";
            }
        }
        List<String> missing = new ArrayList<>(slots.keySet());
        missing.removeAll(bySlot.keySet());
        if (!missing.isEmpty()) {
            return "ngày " + date + " thiếu ô " + String.join(", ", missing);
        }
        for (MenuSlot slot : ctx.slots()) {
            Long dishId = bySlot.get(slot.key());
            boolean repeated = ctx.slots().stream()
                    .filter(s -> s.session() == slot.session() && s.index() < slot.index())
                    .anyMatch(s -> dishId.equals(bySlot.get(s.key())));
            if (repeated) {
                return "ngày " + date + " lặp một món trong cùng bữa " + slot.session();
            }
        }
        cells.put(date, bySlot);
        return null;
    }

    private static LocalDate date(JsonNode day) {
        if (!day.path("date").isString()) {
            return null;
        }
        try {
            return LocalDate.parse(day.path("date").asString().trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    static String sanitizeReason(String reason) {
        if (reason == null) {
            return "";
        }
        String noDigits = reason.replaceAll("\\p{Cntrl}", " ").replaceAll("\\p{Nd}+([.,]\\p{Nd}+)?\\s*%?", " ");
        List<String> kept = new ArrayList<>();
        for (String sentence : SENTENCE_END.split(noDigits)) {
            String lower = sentence.toLowerCase(Locale.ROOT);
            if (MEDICAL_TERMS.stream().noneMatch(lower::contains)) {
                kept.add(sentence.replaceAll("\\s+", " ").trim());
            }
        }
        String joined = String.join(" ", kept.stream().filter(s -> !s.isBlank()).toList())
                .replaceAll("\\s+([,.;!?])", "$1")
                .trim();
        return joined.length() <= REASON_MAX
                ? joined
                : joined.substring(0, REASON_MAX).trim();
    }
}
