package com.carenest.service.health.impl;

import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthExamForm.HealthExamAddress;
import com.carenest.dto.health.HealthExamForm.HealthExamAdmin;
import com.carenest.dto.health.HealthExamForm.HealthExamCompanion;
import com.carenest.dto.health.HealthExamForm.HealthExamConclusion;
import com.carenest.dto.health.HealthExamForm.HealthExamDevelopment;
import com.carenest.dto.health.HealthExamForm.HealthExamHistory;
import com.carenest.dto.health.HealthExamForm.HealthExamImmunization;
import com.carenest.dto.health.HealthExamForm.HealthExamNutrition;
import com.carenest.dto.health.HealthExamForm.HealthExamVitals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Danh mục phiếu khám Phụ lục II, chép từ FE {@code models/children/healthExamForm.js}: lựa chọn của từng ô (mã, nhãn,
 * bất thường), nhãn mục, phiếu mới. Ô gọi theo đường dẫn như khóa lỗi, vd. {@code exam.vitals.pulseStatus}.
 */
final class HealthExamCatalog {

    static final String YES = "YES";
    static final String NO = "NO";
    static final String NORMAL = "NORMAL";
    static final String ACTION_NONE = "NONE";
    static final String ACTION_ADVICE = "ADVICE";
    static final String ACTION_REFERRAL = "REFERRAL";
    static final String FINDING_NO_NEW = "NO_NEW_FINDING";
    static final String FINDING_ADVICE = "ADVICE";
    static final String FINDING_NEW = "NEW_FINDING";
    static final String TEMPERATURE = "temperatureC";
    static final String PULSE = "pulse";
    static final String AUTISM_RISK = "autismRisk";
    static final String BEHAVIOR_NOTE = "behaviorNote";
    // "Có" ở 1 trong 2 câu tâm lý, hành vi ⇒ bác sĩ ghi rõ biểu hiện
    static final List<String> BEHAVIOR_NOTE_TRIGGERS = List.of("prolongedSadness", "selfHarmRisk");

    static final String FLAG_NORMAL = "Bình thường";
    static final String FLAG_TB_RISK = "Có nguy cơ mắc lao (tiền sử tiếp xúc)";
    static final String FLAG_HEALTH_ISSUE = "Có vấn đề về sức khỏe";

    record Option(String code, String label, boolean abnormal) {}

    /** Câu chọn một trong một phần của phiếu; {@code value} đọc mã đã chọn từ phần đó. */
    record ChoiceItem<S>(String key, String label, List<Option> options, Function<S, String> value) {}

    /** Mục tiền sử Có/Không; "Có" kèm tên bệnh ({@code textKey}) hoặc ngày tiếp xúc lao ({@code dateKey}). */
    record HistoryItem(
            String key,
            String label,
            Function<HealthExamHistory, String> answer,
            String textKey,
            String textLabel,
            Function<HealthExamHistory, String> text,
            String dateKey,
            String dateLabel) {}

    /** Dấu hiệu sinh tồn: số đo (khoảng nhập min–max) và trạng thái bác sĩ chọn. */
    record VitalItem(
            String key,
            String statusKey,
            String label,
            String unit,
            int min,
            int max,
            List<Option> options,
            Function<HealthExamVitals, BigDecimal> value,
            Function<HealthExamVitals, String> status) {

        /** Nhãn bỏ đơn vị trong ngoặc: "Mạch (lần/phút)" ⇒ "Mạch". */
        String name() {
            return label.replaceFirst(" \\(.*\\)$", "");
        }
    }

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final List<Option> NO_YES = List.of(opt(NO, "Không"), opt(YES, "Có", true));
    // Mẫu in "Có" trước ở 2 câu tâm lý, hành vi
    private static final List<Option> YES_FIRST = List.of(opt(YES, "Có", true), opt(NO, "Không"));
    // Phần IV, V: "Có" là đạt (phát triển bình thường, đã tiêm), "Không" là điểm cần xem lại
    private static final List<Option> HAS_NO = List.of(opt(YES, "Có"), opt(NO, "Không", true));
    private static final List<Option> NORMAL_OR_ABNORMAL =
            List.of(opt(NORMAL, "Bình thường"), opt("ABNORMAL", "Không bình thường", true));

    // Sinh non "Có" chỉ là thông tin hành chính, không phải bất thường của buổi khám
    static final List<Option> PREMATURE_OPTIONS = List.of(opt(NO, "Không"), opt(YES, "Có"));
    static final List<Option> COMPANION_OPTIONS =
            List.of(opt(YES, "Có người đi cùng"), opt(NO, "Không có người đi cùng"));

    static final List<HistoryItem> HISTORY_ITEMS = List.of(
            new HistoryItem(
                    "self",
                    "Tiền sử bản thân",
                    HealthExamHistory::self,
                    "selfText",
                    "Tên bệnh của trẻ",
                    HealthExamHistory::selfText,
                    null,
                    null),
            new HistoryItem(
                    "family",
                    "Tiền sử gia đình",
                    HealthExamHistory::family,
                    "familyText",
                    "Tên bệnh trong gia đình",
                    HealthExamHistory::familyText,
                    null,
                    null),
            new HistoryItem(
                    "tbContact",
                    "Tiền sử tiếp xúc với người bệnh lao",
                    HealthExamHistory::tbContact,
                    null,
                    null,
                    null,
                    "tbContactDate",
                    "Ngày tiếp xúc gần nhất"));

    static final List<VitalItem> VITAL_ITEMS = List.of(
            new VitalItem(
                    TEMPERATURE,
                    "temperatureStatus",
                    "Nhiệt độ (°C)",
                    "°C",
                    34,
                    42,
                    List.of(
                            opt(NORMAL, "Bình thường"),
                            opt("FEVER", "Sốt", true),
                            opt("HYPOTHERMIA", "Hạ thân nhiệt", true)),
                    HealthExamVitals::temperatureC,
                    HealthExamVitals::temperatureStatus),
            new VitalItem(
                    PULSE,
                    "pulseStatus",
                    "Mạch (lần/phút)",
                    "lần/phút",
                    40,
                    220,
                    List.of(opt(NORMAL, "Bình thường"), opt("FAST", "Nhanh", true)),
                    HealthExamVitals::pulse,
                    HealthExamVitals::pulseStatus),
            new VitalItem(
                    "respiratoryRate",
                    "respiratoryStatus",
                    "Nhịp thở (lần/phút)",
                    "lần/phút",
                    10,
                    90,
                    List.of(opt(NORMAL, "Bình thường"), opt("FAST", "Thở nhanh", true), opt("SLOW", "Thở chậm", true)),
                    HealthExamVitals::respiratoryRate,
                    HealthExamVitals::respiratoryStatus));

    static final List<Option> HEAD_STATUS_OPTIONS =
            List.of(opt(NORMAL, "Bình thường"), opt("LARGE", "Đầu to", true), opt("SMALL", "Đầu nhỏ", true));

    // Lựa chọn của bác sĩ theo mẫu Bộ Y tế; hệ thống không tự gắn
    static final List<Option> NUTRITION_STATUS_OPTIONS = List.of(
            opt(NORMAL, "Bình thường"),
            opt("NUTRITIONAL_EDEMA", "Phù dinh dưỡng", true),
            opt("ANEMIA_SIGNS", "Dấu hiệu thiếu máu", true),
            opt("RICKETS_SIGNS", "Dấu hiệu còi xương", true),
            opt("MALNUTRITION", "Suy dinh dưỡng", true),
            opt("OVERWEIGHT", "Thừa cân/béo phì", true));

    static final List<ChoiceItem<HealthExamDevelopment>> DEVELOPMENT_ITEMS = List.of(
            new ChoiceItem<>(
                    "mental",
                    "Phát triển tinh thần bình thường của trẻ theo độ tuổi",
                    HAS_NO,
                    HealthExamDevelopment::mental),
            new ChoiceItem<>(
                    "motor",
                    "Phát triển vận động bình thường của trẻ theo độ tuổi",
                    HAS_NO,
                    HealthExamDevelopment::motor),
            // Chỉ hỏi trẻ 16–30 tháng (HealthExamReference.autismQuestion)
            new ChoiceItem<>(
                    AUTISM_RISK,
                    "Trẻ có nguy cơ tự kỷ (với trẻ từ 16–30 tháng tuổi)",
                    NO_YES,
                    HealthExamDevelopment::autismRisk));

    static final List<ChoiceItem<HealthExamImmunization>> IMMUNIZATION_ITEMS = List.of(
            new ChoiceItem<>("bcg", "Lao (sơ sinh)", HAS_NO, HealthExamImmunization::bcg),
            new ChoiceItem<>("hepB1", "Viêm gan B mũi 1 (sơ sinh)", HAS_NO, HealthExamImmunization::hepB1),
            new ChoiceItem<>(
                    "fullSchedule",
                    "Tiêm chủng đầy đủ các loại vắc xin theo độ tuổi",
                    HAS_NO,
                    HealthExamImmunization::fullSchedule));

    /** 24 mục phần VI theo thứ tự mẫu, đọc từ {@code exam.clinical}. */
    static final List<ChoiceItem<Map<String, String>>> CLINICAL_ITEMS = List.of(
            clinical(
                    "skinColor",
                    "Màu sắc da",
                    List.of(
                            opt("PINK", "Hồng hào"),
                            opt("PALE", "Nhợt", true),
                            opt("CYANOTIC", "Tím", true),
                            opt("YELLOW", "Vàng", true),
                            opt("DARK", "Sạm da", true))),
            clinical(
                    "palms",
                    "Lòng bàn tay",
                    List.of(opt(NORMAL, "Bình thường (không nhợt)"), opt("PALE", "Không bình thường (nhợt)", true))),
            clinical("headShape", "Kích thước và hình dạng đầu", NORMAL_OR_ABNORMAL),
            clinical(
                    "neckMotion", "Vận động cổ", List.of(opt(NORMAL, "Bình thường"), opt("LIMITED", "Giới hạn", true))),
            clinical("neckMass", "Khối bất thường", NO_YES),
            clinical(
                    "eyePosition",
                    "Vị trí 2 mắt",
                    List.of(opt(NORMAL, "Bình thường"), opt("WIDE", "2 mắt xa nhau", true))),
            clinical(
                    "eyelids",
                    "Mí mắt và kết mạc",
                    List.of(
                            opt(NORMAL, "Bình thường"),
                            opt("SWOLLEN", "Sưng/đỏ", true),
                            opt("DISCHARGE", "Chảy ghèn/mủ", true))),
            clinical("strabismus", "Lác mắt", NO_YES),
            clinical("hearing", "Đáp ứng với âm thanh", NORMAL_OR_ABNORMAL),
            clinical("earLump", "Có khối sưng sau tai", NO_YES),
            clinical("earDischarge", "Dấu hiệu chảy mủ, nước tai", NO_YES),
            clinical(
                    "noseShape",
                    "Hình dạng mũi",
                    List.of(
                            opt(NORMAL, "Bình thường"),
                            opt("BROAD", "Mũi to, dày", true),
                            opt("APLASIA", "Bất sản xương mũi", true))),
            clinical("runnyNose", "Chảy nước mũi", NO_YES),
            clinical("nasalCongestion", "Nghẹt mũi", NO_YES),
            clinical(
                    "mouthShape",
                    "Hình dạng miệng",
                    List.of(opt(NORMAL, "Bình thường"), opt("CLEFT", "Sứt môi, chẻ vòm", true))),
            clinical(
                    "tongueShape",
                    "Hình dạng lưỡi",
                    List.of(opt(NORMAL, "Bình thường"), opt("LARGE", "Lưỡi to bè", true))),
            clinical("tongueTie", "Dính thắng lưỡi", NO_YES),
            clinical("smallChin", "Cằm nhỏ, tụt về sau", NO_YES),
            clinical("dentalIssue", "Vết sâu, mảng bám, lỗ trên răng", NO_YES),
            clinical("asymmetricMotion", "Vận động không đối xứng", NO_YES),
            clinical("spine", "Kiểm tra lưng, cột sống", NORMAL_OR_ABNORMAL),
            clinical("gait", "Quan sát dáng đi", NORMAL_OR_ABNORMAL),
            clinical("prolongedSadness", "Có biểu hiện buồn bã, lo âu, mất ngủ kéo dài không", YES_FIRST),
            clinical("selfHarmRisk", "Có hành vi bất thường hoặc nguy cơ tự gây hại không", YES_FIRST));

    static final List<Option> ISSUE_KIND_OPTIONS = List.of(
            opt("NEW", "Vấn đề mới hoặc có thay đổi"), opt("KNOWN", "Vấn đề đã biết, không thay đổi so với trước"));
    // Mẫu FOLLOW_UP: một câu so với vấn đề đang theo dõi thay cho cờ kết luận và hướng xử lý
    static final List<Option> FOLLOW_UP_FINDING_OPTIONS = List.of(
            opt(FINDING_NO_NEW, "Chưa ghi nhận thêm bất thường"),
            opt(FINDING_ADVICE, "Tư vấn"),
            opt(FINDING_NEW, "Có thêm bất thường mới"));
    static final List<Option> ACTION_OPTIONS = List.of(
            opt(ACTION_NONE, "Không cần tư vấn hay chuyển khám"),
            opt(ACTION_ADVICE, "Tư vấn"),
            opt(ACTION_REFERRAL, "Chuyển khám chuyên khoa"));

    private static final Map<String, List<Option>> OPTIONS = optionsByField();

    private HealthExamCatalog() {}

    /** Mã 24 mục khám lâm sàng theo thứ tự mẫu (không gồm {@code behaviorNote}). */
    static List<String> clinicalKeys() {
        return CLINICAL_ITEMS.stream().map(ChoiceItem::key).toList();
    }

    /** Lựa chọn của ô chọn theo đường dẫn ({@code exam.clinical.skinColor}); ô không phải ô chọn ⇒ rỗng. */
    static List<Option> options(String field) {
        return OPTIONS.getOrDefault(field, List.of());
    }

    static boolean isOption(String field, String code) {
        return find(options(field), code) != null;
    }

    /** Nhãn của mã đã chọn; mã ngoài danh mục ⇒ {@code ""}. */
    static String optionLabel(String field, String code) {
        return labelOf(options(field), code);
    }

    static boolean isAbnormal(String field, String code) {
        Option option = find(options(field), code);
        return option != null && option.abnormal();
    }

    static Option find(List<Option> options, String code) {
        for (Option option : options) {
            if (option.code().equals(code)) {
                return option;
            }
        }
        return null;
    }

    static String labelOf(List<Option> options, String code) {
        Option option = find(options, code);
        return option == null ? "" : option.label();
    }

    /**
     * Phiếu mới (mẫu {@code healthExamForm.js} của FE): phần VI chọn sẵn kết quả bình thường (lựa chọn đầu; 2 câu tâm lý, hành vi là "Không");
     * phần cần hỏi hoặc đo để trống. Service điền hành chính, người đi cùng, tiền sử khai báo sau.
     */
    static HealthExamForm template() {
        Map<String, String> clinical = new LinkedHashMap<>();
        CLINICAL_ITEMS.forEach(item -> clinical.put(
                item.key(),
                item.options().stream()
                        .filter(o -> !o.abnormal())
                        .findFirst()
                        .orElseThrow()
                        .code()));
        clinical.put(BEHAVIOR_NOTE, "");
        return new HealthExamForm(
                0,
                new HealthExamAdmin(
                        "", "", null, "", "", "", "", new HealthExamAddress("", "", ""), "", blankCompanion()),
                new HealthExamHistory("", "", "", "", "", null),
                new HealthExamVitals(null, "", null, "", null, ""),
                new HealthExamNutrition(null, null, "", null, List.of()),
                new HealthExamDevelopment("", "", ""),
                new HealthExamImmunization("", "", ""),
                clinical,
                new HealthExamConclusion(false, false, false, "", "", "", "", "", "", "", ""));
    }

    static HealthExamCompanion blankCompanion() {
        return new HealthExamCompanion(null, "", "", "", "");
    }

    /** Ngày trong câu hiển thị: dd/MM/yyyy; null ⇒ {@code ""}. */
    static String formatDate(LocalDate date) {
        return date == null ? "" : DISPLAY_DATE.format(date);
    }

    /** Số kiểu vi-VN như FE ({@code toLocaleString('vi-VN')}): phẩy thập phân, chấm nhóm nghìn, tối đa digits số lẻ. */
    static String formatDecimal(BigDecimal value, int digits) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        symbols.setMinusSign('-');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        format.setMaximumFractionDigits(digits);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    private static Option opt(String code, String label) {
        return new Option(code, label, false);
    }

    private static Option opt(String code, String label, boolean abnormal) {
        return new Option(code, label, abnormal);
    }

    private static ChoiceItem<Map<String, String>> clinical(String key, String label, List<Option> options) {
        return new ChoiceItem<>(key, label, options, values -> values.get(key));
    }

    private static Map<String, List<Option>> optionsByField() {
        Map<String, List<Option>> map = new LinkedHashMap<>();
        map.put("exam.admin.premature", PREMATURE_OPTIONS);
        map.put("exam.admin.hasCompanion", COMPANION_OPTIONS);
        HISTORY_ITEMS.forEach(i -> map.put("exam.history." + i.key(), NO_YES));
        VITAL_ITEMS.forEach(i -> map.put("exam.vitals." + i.statusKey(), i.options()));
        map.put("exam.nutrition.headStatus", HEAD_STATUS_OPTIONS);
        map.put("exam.nutrition.statuses", NUTRITION_STATUS_OPTIONS);
        DEVELOPMENT_ITEMS.forEach(i -> map.put("exam.development." + i.key(), i.options()));
        IMMUNIZATION_ITEMS.forEach(i -> map.put("exam.immunization." + i.key(), i.options()));
        CLINICAL_ITEMS.forEach(i -> map.put("exam.clinical." + i.key(), i.options()));
        map.put("exam.conclusion.issueKind", ISSUE_KIND_OPTIONS);
        map.put("exam.conclusion.followUpFinding", FOLLOW_UP_FINDING_OPTIONS);
        map.put("exam.conclusion.action", ACTION_OPTIONS);
        return Map.copyOf(map);
    }
}
