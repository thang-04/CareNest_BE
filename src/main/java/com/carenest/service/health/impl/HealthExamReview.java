package com.carenest.service.health.impl;

import static com.carenest.service.health.impl.HealthExamCatalog.AUTISM_RISK;
import static com.carenest.service.health.impl.HealthExamCatalog.BEHAVIOR_NOTE;
import static com.carenest.service.health.impl.HealthExamCatalog.CLINICAL_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.DEVELOPMENT_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.HISTORY_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.IMMUNIZATION_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.NO;
import static com.carenest.service.health.impl.HealthExamCatalog.NORMAL;
import static com.carenest.service.health.impl.HealthExamCatalog.NUTRITION_STATUS_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.PULSE;
import static com.carenest.service.health.impl.HealthExamCatalog.TEMPERATURE;
import static com.carenest.service.health.impl.HealthExamCatalog.VITAL_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.YES;
import static com.carenest.service.health.impl.HealthExamCatalog.formatDecimal;

import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthExamForm.HealthExamDevelopment;
import com.carenest.dto.health.HealthExamForm.HealthExamImmunization;
import com.carenest.dto.health.HealthExamForm.HealthExamVitals;
import com.carenest.dto.health.HealthExamReference;
import com.carenest.dto.health.HealthExamReference.HealthExamHeadReference;
import com.carenest.dto.health.HealthExamReference.HealthExamRange;
import com.carenest.dto.health.HealthExamReference.HealthExamTemperatureReference;
import com.carenest.dto.health.HealthExamReference.HealthExamVitalReference;
import com.carenest.dto.health.HealthExamReviewPoints;
import com.carenest.dto.health.HealthExamReviewPoints.HealthExamCombinedHint;
import com.carenest.dto.health.HealthExamReviewPoints.HealthExamHint;
import com.carenest.dto.health.HealthExamReviewPoints.HealthExamOtherPoint;
import com.carenest.dto.health.HealthExamReviewPoints.HealthExamVitalPoint;
import com.carenest.service.health.impl.HealthExamCatalog.ChoiceItem;
import com.carenest.service.health.impl.HealthExamCatalog.HistoryItem;
import com.carenest.service.health.impl.HealthExamCatalog.Option;
import com.carenest.service.health.impl.HealthExamCatalog.VitalItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mốc tham khảo theo tuổi tại ngày khám và "Điểm cần bác sĩ xem lại" (như {@code healthScreeningRule.js}, {@code healthExamHints.js} của FE). Chỉ là gợi ý cho
 * người nhập, bác sĩ quyết định; không dùng số đo chiều cao, cân nặng, vòng đầu.
 */
final class HealthExamReview {

    static final String VITAL_REFERENCE_LABEL =
            "ngưỡng tham khảo tạm (mạch, nhịp thở theo AHA PALS 2020; nhiệt độ đo ở nách) — chờ bác sĩ xác nhận";
    // Chuẩn vòng đầu WHO chỉ tới 60 tháng ⇒ trẻ lớn hơn không đo vòng đầu
    static final int HEAD_MAX_AGE_MONTHS = 60;
    private static final int AUTISM_FROM_MONTHS = 16;
    private static final int AUTISM_TO_MONTHS = 30;

    // Mạch, nhịp thở lúc thức theo AHA PALS 2020; nhiệt độ đo ở nách (tạm, chờ bác sĩ xác nhận)
    private record Band(int maxMonths, String label, HealthExamRange pulse, HealthExamRange respiratoryRate) {}

    private static final List<Band> BANDS = List.of(
            new Band(11, "trẻ dưới 12 tháng", new HealthExamRange(100, 180), new HealthExamRange(30, 53)),
            new Band(35, "trẻ 12–35 tháng", new HealthExamRange(98, 140), new HealthExamRange(22, 37)),
            new Band(71, "trẻ 3–5 tuổi", new HealthExamRange(80, 120), new HealthExamRange(20, 28)),
            new Band(Integer.MAX_VALUE, "trẻ từ 6 tuổi", new HealthExamRange(75, 118), new HealthExamRange(18, 25)));
    private static final HealthExamTemperatureReference TEMPERATURE_REFERENCE =
            new HealthExamTemperatureReference(new BigDecimal("37.5"), new BigDecimal("36"));

    // Khoảng vòng đầu TẠM (chờ bảng WHO): 45,5–51 cm lúc 24 tháng, mỗi tháng +0,06
    private static final BigDecimal HEAD_LOW_AT_24 = new BigDecimal("45.5");
    private static final BigDecimal HEAD_HIGH_AT_24 = new BigDecimal("51");
    private static final BigDecimal HEAD_STEP = new BigDecimal("0.06");

    private static final String FEVER = "FEVER";
    private static final String HYPOTHERMIA = "HYPOTHERMIA";
    private static final String FAST = "FAST";
    private static final String SLOW = "SLOW";
    // Mạch dưới khoảng: mẫu không có ô "mạch chậm"
    private static final String BELOW = "BELOW";
    private static final String WATCH_TEXT = "Gợi ý: cần theo dõi";

    private HealthExamReview() {}

    /** Số tháng tròn: chưa tới ngày sinh trong tháng thì chưa tính tháng đó. */
    static int ageMonths(LocalDate dateOfBirth, LocalDate on) {
        return (on.getYear() - dateOfBirth.getYear()) * 12
                + (on.getMonthValue() - dateOfBirth.getMonthValue())
                - (on.getDayOfMonth() < dateOfBirth.getDayOfMonth() ? 1 : 0);
    }

    static HealthExamReference reference(LocalDate dateOfBirth, LocalDate on) {
        int age = ageMonths(dateOfBirth, on);
        Band band = BANDS.stream().filter(b -> age <= b.maxMonths()).findFirst().orElseThrow();
        boolean headApplies = age <= HEAD_MAX_AGE_MONTHS;
        BigDecimal headShift = HEAD_STEP.multiply(BigDecimal.valueOf(age - 24L));
        return new HealthExamReference(
                age,
                new HealthExamVitalReference(band.label(), band.pulse(), band.respiratoryRate(), TEMPERATURE_REFERENCE),
                headApplies
                        ? new HealthExamHeadReference(HEAD_LOW_AT_24.add(headShift), HEAD_HIGH_AT_24.add(headShift))
                        : null,
                headApplies,
                age >= AUTISM_FROM_MONTHS && age <= AUTISM_TO_MONTHS,
                VITAL_REFERENCE_LABEL);
    }

    static HealthExamReviewPoints points(HealthExamForm exam, HealthExamReference reference) {
        HealthExamForm e = exam == null ? HealthExamNormalizer.blank() : exam;
        List<HealthExamVitalPoint> vitals = vitalPoints(e.vitals(), reference.vitals());
        return new HealthExamReviewPoints(
                vitals,
                vitalsHint(vitals),
                combined(e, reference.headApplies()),
                others(e, reference.autismQuestion()),
                VITAL_REFERENCE_LABEL);
    }

    // Từng chỉ số: số ngoài khoảng theo tuổi; số trong khoảng mà bác sĩ chọn trạng thái bất thường vẫn liệt kê
    private static List<HealthExamVitalPoint> vitalPoints(HealthExamVitals vitals, HealthExamVitalReference ref) {
        List<HealthExamVitalPoint> points = new ArrayList<>();
        for (VitalItem item : VITAL_ITEMS) {
            BigDecimal value = item.value().apply(vitals);
            String hint = hintOf(item.key(), value, ref);
            if (hint != null && !hint.equals(NORMAL)) {
                points.add(new HealthExamVitalPoint(item.key(), outOfRangeText(item, value, hint, ref)));
                continue;
            }
            Option picked = HealthExamCatalog.find(item.options(), item.status().apply(vitals));
            if (picked != null && picked.abnormal()) {
                points.add(new HealthExamVitalPoint(
                        item.key(), item.name() + ": " + picked.label().toLowerCase(Locale.ROOT) + " (theo bác sĩ)"));
            }
        }
        return points;
    }

    // Trạng thái gợi ý từ số đo; null khi chưa có số
    private static String hintOf(String key, BigDecimal value, HealthExamVitalReference ref) {
        if (value == null || ref == null) {
            return null;
        }
        if (key.equals(TEMPERATURE)) {
            if (value.compareTo(ref.temperature().feverFrom()) >= 0) {
                return FEVER;
            }
            return value.compareTo(ref.temperature().hypothermiaBelow()) < 0 ? HYPOTHERMIA : NORMAL;
        }
        HealthExamRange range = key.equals(PULSE) ? ref.pulse() : ref.respiratoryRate();
        if (value.compareTo(BigDecimal.valueOf(range.max())) > 0) {
            return FAST;
        }
        if (value.compareTo(BigDecimal.valueOf(range.min())) < 0) {
            return key.equals(PULSE) ? BELOW : SLOW;
        }
        return NORMAL;
    }

    private static String outOfRangeText(VitalItem item, BigDecimal value, String hint, HealthExamVitalReference ref) {
        if (item.key().equals(TEMPERATURE)) {
            String limit = hint.equals(FEVER)
                    ? "từ " + formatDecimal(ref.temperature().feverFrom(), 1) + " °C"
                    : "dưới " + formatDecimal(ref.temperature().hypothermiaBelow(), 1) + " °C";
            String word = hint.equals(FEVER) ? "sốt" : "hạ thân nhiệt";
            return "Nhiệt độ " + formatDecimal(value, 1) + " °C: " + word + " (" + limit + ")";
        }
        HealthExamRange range = item.key().equals(PULSE) ? ref.pulse() : ref.respiratoryRate();
        String side = hint.equals(FAST) ? "trên" : "dưới";
        return item.name() + " " + formatDecimal(value, 0) + " " + item.unit() + ", " + side + " khoảng tham khảo "
                + range.min() + "–" + range.max() + " của " + ref.label();
    }

    // Gợi ý gộp phần II: 1/3 chỉ số ⇒ cần theo dõi; từ 2/3 ⇒ bác sĩ xem lại trước khi kết luận
    private static HealthExamHint vitalsHint(List<HealthExamVitalPoint> points) {
        int count = points.size();
        if (count >= 2) {
            return new HealthExamHint(
                    count,
                    VITAL_ITEMS.size(),
                    "ALERT",
                    "Gợi ý: nhiều dấu hiệu sinh tồn bất thường, bác sĩ xem lại trước khi kết luận");
        }
        return count == 1
                ? new HealthExamHint(count, VITAL_ITEMS.size(), "WATCH", WATCH_TEXT)
                : new HealthExamHint(count, VITAL_ITEMS.size(), "NONE", "");
    }

    // Vòng đầu + 2 câu phần IV: 1 dấu hiệu ⇒ theo dõi; từ 2 ⇒ cân nhắc chuyển khám
    private static HealthExamCombinedHint combined(HealthExamForm exam, boolean headApplies) {
        List<String> signs = new ArrayList<>();
        String head = exam.nutrition().headStatus();
        if (headApplies && "LARGE".equals(head)) {
            signs.add("Vòng đầu to");
        }
        if (headApplies && "SMALL".equals(head)) {
            signs.add("Vòng đầu nhỏ");
        }
        if (NO.equals(exam.development().mental())) {
            signs.add("Phát triển tinh thần chưa bình thường theo độ tuổi");
        }
        if (NO.equals(exam.development().motor())) {
            signs.add("Phát triển vận động chưa bình thường theo độ tuổi");
        }
        int total = headApplies ? 3 : 2;
        if (signs.size() >= 2) {
            return new HealthExamCombinedHint(
                    List.copyOf(signs), total, "REFERRAL", "Gợi ý: cân nhắc chuyển khám chuyên khoa");
        }
        return signs.size() == 1
                ? new HealthExamCombinedHint(List.copyOf(signs), total, "WATCH", WATCH_TEXT)
                : new HealthExamCombinedHint(List.of(), total, "NONE", "");
    }

    private static List<HealthExamOtherPoint> others(HealthExamForm exam, boolean autismQuestion) {
        List<String> out = new ArrayList<>();
        for (HistoryItem item : HISTORY_ITEMS) {
            if (!YES.equals(item.answer().apply(exam.history()))) {
                continue;
            }
            String detail = item.text() == null
                    ? ""
                    : HealthExamNormalizer.trim(item.text().apply(exam.history()));
            out.add(item.label() + ": Có" + (detail.isEmpty() ? "" : " (" + detail + ")"));
        }
        List<String> statuses = exam.nutrition().statuses() == null
                ? List.of()
                : exam.nutrition().statuses();
        for (String status : statuses) {
            Option option = HealthExamCatalog.find(NUTRITION_STATUS_OPTIONS, status);
            if (option != null && option.abnormal()) {
                out.add("Tình trạng dinh dưỡng: " + option.label());
            }
        }
        for (ChoiceItem<HealthExamDevelopment> item : DEVELOPMENT_ITEMS) {
            if (item.key().equals(AUTISM_RISK)
                    && autismQuestion
                    && YES.equals(item.value().apply(exam.development()))) {
                out.add(item.label() + ": Có");
            }
        }
        for (ChoiceItem<HealthExamImmunization> item : IMMUNIZATION_ITEMS) {
            if (NO.equals(item.value().apply(exam.immunization()))) {
                out.add("Tiêm chủng — " + item.label() + ": Không");
            }
        }
        Map<String, String> clinical = exam.clinical() == null ? Map.of() : exam.clinical();
        for (ChoiceItem<Map<String, String>> item : CLINICAL_ITEMS) {
            Option option = HealthExamCatalog.find(item.options(), item.value().apply(clinical));
            if (option != null && option.abnormal()) {
                out.add(item.label() + ": " + option.label());
            }
        }
        String note = HealthExamNormalizer.trim(clinical.get(BEHAVIOR_NOTE));
        if (!note.isEmpty()) {
            out.add("Ghi rõ (tâm lý, hành vi): " + note);
        }
        return out.stream().map(HealthExamOtherPoint::new).toList();
    }
}
