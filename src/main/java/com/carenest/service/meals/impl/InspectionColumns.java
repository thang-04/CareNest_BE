package com.carenest.service.meals.impl;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.InspectionTab;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bảng cột của từng mẫu biên bản (QĐ 1246/QĐ-BYT): khóa ô, kiểu, ô hệ thống điền. Dùng chung cho kiểm ô nhập khi lưu,
 * đếm ô Đạt / Không đạt khi ký và gộp dòng nguồn mới vào bản nháp. Khớp {@code INSPECTION_COLUMNS} ở client.
 */
final class InspectionColumns {

    enum Kind {
        TEXT,
        NUMBER,
        TIME,
        DATE,
        TIMESTAMP,
        CHECK,
        PASSFAIL,
        SELECT
    }

    /** {@code system}: hệ thống điền từ dữ liệu nguồn, mỗi lần đọc nháp lấy lại giá trị mới nhất. */
    record Col(Kind kind, boolean system) {}

    static final String KEY = "key";
    static final String SENSORY = "sensory";
    static final String RAPID_TEST = "rapidTest";
    static final String PASS = "PASS";
    static final String FAIL = "FAIL";

    private static final int MAX_TEXT = 500;
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final Set<String> CHECKED = Set.of("YES", "OK");
    private static final Set<String> STORAGE = Set.of("ROOM", "COOL", "FROZEN");

    private static final Map<InspectionTab, Map<String, Col>> COLUMNS = build();

    private InspectionColumns() {}

    private static Col text(boolean system) {
        return new Col(Kind.TEXT, system);
    }

    private static Col of(Kind kind) {
        return new Col(kind, false);
    }

    private static Map<InspectionTab, Map<String, Col>> build() {
        Map<String, Col> common = new HashMap<>();
        for (String key : List.of(KEY, "session", "block", "unit")) {
            common.put(key, text(true));
        }
        common.put("dishId", new Col(Kind.NUMBER, true));
        common.put("foodId", new Col(Kind.NUMBER, true));
        common.put("note", text(false));
        common.put(SENSORY, of(Kind.PASSFAIL));

        Map<InspectionTab, Map<String, Col>> result = new HashMap<>();

        Map<String, Col> fresh = new HashMap<>(common);
        fresh.put("name", text(true));
        fresh.put("receivedAt", of(Kind.TIME));
        fresh.put("quantity", of(Kind.NUMBER));
        for (String key : List.of("supplierName", "supplierContact", "deliveryPerson")) {
            fresh.put(key, text(false));
        }
        for (String key : List.of("invoice", "vetCert", "quarantineCert")) {
            fresh.put(key, of(Kind.CHECK));
        }
        fresh.put("rapidTest", of(Kind.PASSFAIL));
        result.put(InspectionTab.FRESH, fresh);

        Map<String, Col> dry = new HashMap<>(common);
        dry.put("name", text(true));
        dry.put("receivedAt", of(Kind.DATE));
        dry.put("expiryDate", of(Kind.DATE));
        dry.put("quantity", of(Kind.NUMBER));
        for (String key :
                List.of("manufacturer", "manufacturerAddress", "supplierName", "supplierContact", "deliveryPerson")) {
            dry.put(key, text(false));
        }
        dry.put("storageCondition", of(Kind.SELECT));
        dry.put("invoice", of(Kind.CHECK));
        result.put(InspectionTab.DRY, dry);

        Map<String, Col> process = new HashMap<>(common);
        dishRowColumns(process);
        process.put("mainIngredients", text(false));
        process.put("prepDoneAt", of(Kind.TIME));
        process.put("cookDoneAt", of(Kind.TIME));
        for (String key : List.of("hygienePeople", "hygieneEquipment", "hygieneArea")) {
            process.put(key, of(Kind.CHECK));
        }
        result.put(InspectionTab.PROCESS, process);

        Map<String, Col> serve = new HashMap<>(common);
        dishRowColumns(serve);
        serve.put("servedAt", of(Kind.TIME));
        serve.put("eatStartAt", of(Kind.TIME));
        serve.put("utensils", of(Kind.CHECK));
        result.put(InspectionTab.SERVE, serve);

        Map<String, Col> sample = new HashMap<>(common);
        dishRowColumns(sample);
        for (String key : List.of("weight", "container", "temperature")) {
            sample.put(key, text(true));
        }
        sample.put("takenAt", new Col(Kind.TIMESTAMP, true));
        sample.put("destroyedAt", new Col(Kind.TIMESTAMP, true));
        sample.put("qualityNote", text(false));
        // M5: bếp ghi, PHT soát; không có cột cảm quan, ghi chú
        sample.remove(SENSORY);
        sample.remove("note");
        result.put(InspectionTab.SAMPLE, sample);

        result.replaceAll((tab, columns) -> Map.copyOf(columns));
        return Map.copyOf(result);
    }

    private static void dishRowColumns(Map<String, Col> columns) {
        columns.put("sessionLabel", text(true));
        columns.put("dishName", text(true));
        columns.put("servings", new Col(Kind.NUMBER, true));
    }

    /** Khóa ô hệ thống điền của mẫu. */
    static Set<String> systemKeys(InspectionTab tab) {
        Set<String> keys = new HashSet<>();
        COLUMNS.get(tab).forEach((key, col) -> {
            if (col.system()) {
                keys.add(key);
            }
        });
        return keys;
    }

    /**
     * Kiểm và làm sạch dòng client gửi: chỉ nhận khóa ô của mẫu, đúng kiểu, mỗi dòng một khóa dòng duy nhất; giá trị null
     * bỏ đi. Sai ⇒ 400 {@code INSPECTION_ROW_INVALID} nêu ô sai.
     */
    static List<Map<String, Object>> sanitize(InspectionTab tab, List<Map<String, Object>> rows) {
        Map<String, Col> columns = COLUMNS.get(tab);
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> clean = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> out = new LinkedHashMap<>();
            row.forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                Col col = columns.get(key);
                if (col == null || !valid(col.kind(), key, value)) {
                    throw GlobalException.of(ApiCode.INSPECTION_ROW_INVALID, key);
                }
                out.put(key, value);
            });
            if (!(out.get(KEY) instanceof String rowKey)
                    || rowKey.isBlank()
                    || rowKey.length() > 80
                    || !seen.add(rowKey)) {
                throw GlobalException.of(ApiCode.INSPECTION_ROW_INVALID, KEY);
            }
            clean.add(out);
        }
        return clean;
    }

    private static boolean valid(Kind kind, String key, Object value) {
        if (kind == Kind.NUMBER) {
            return value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0
                    || value instanceof String s && s.isEmpty();
        }
        if (!(value instanceof String text) || text.length() > (key.equals(KEY) ? 80 : MAX_TEXT)) {
            return false;
        }
        if (text.isEmpty()) {
            return true;
        }
        return switch (kind) {
            case TIME -> TIME.matcher(text).matches();
            case DATE -> isDate(text);
            case CHECK -> CHECKED.contains(text);
            case PASSFAIL -> PASS.equals(text) || FAIL.equals(text);
            case SELECT -> STORAGE.contains(text);
            default -> true;
        };
    }

    private static boolean isDate(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    /**
     * Số dòng Không đạt (cảm quan hoặc xét nghiệm nhanh) chưa ghi biện pháp xử lý ở cột ghi chú (INS-02; QĐ 1246 Điều 3.1,
     * 4.2, 5.5: không đạt phải ghi rõ biện pháp như loại bỏ, trả lại, tiêu hủy).
     */
    static int actionMissing(List<Map<String, Object>> rows) {
        return (int) rows.stream()
                .filter(row -> FAIL.equals(row.get(SENSORY)) || FAIL.equals(row.get(RAPID_TEST)))
                .filter(row -> !(row.get("note") instanceof String note && !note.isBlank()))
                .count();
    }

    /** Số dòng chưa chọn Đạt / Không đạt ở cột cảm quan (INS-02). */
    static int sensoryMissing(List<Map<String, Object>> rows) {
        return (int) rows.stream()
                .filter(row -> !(row.get(SENSORY) instanceof String v && (PASS.equals(v) || FAIL.equals(v))))
                .count();
    }
}
