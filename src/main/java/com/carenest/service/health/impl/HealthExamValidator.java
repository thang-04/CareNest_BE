package com.carenest.service.health.impl;

import static com.carenest.service.health.impl.HealthExamCatalog.ACTION_ADVICE;
import static com.carenest.service.health.impl.HealthExamCatalog.ACTION_NONE;
import static com.carenest.service.health.impl.HealthExamCatalog.ACTION_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.ACTION_REFERRAL;
import static com.carenest.service.health.impl.HealthExamCatalog.AUTISM_RISK;
import static com.carenest.service.health.impl.HealthExamCatalog.BEHAVIOR_NOTE;
import static com.carenest.service.health.impl.HealthExamCatalog.BEHAVIOR_NOTE_TRIGGERS;
import static com.carenest.service.health.impl.HealthExamCatalog.CLINICAL_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.DEVELOPMENT_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.FINDING_ADVICE;
import static com.carenest.service.health.impl.HealthExamCatalog.FINDING_NEW;
import static com.carenest.service.health.impl.HealthExamCatalog.FINDING_NO_NEW;
import static com.carenest.service.health.impl.HealthExamCatalog.FOLLOW_UP_FINDING_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.HEAD_STATUS_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.HISTORY_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.IMMUNIZATION_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.ISSUE_KIND_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.NORMAL;
import static com.carenest.service.health.impl.HealthExamCatalog.NO_YES;
import static com.carenest.service.health.impl.HealthExamCatalog.NUTRITION_STATUS_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.VITAL_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.YES;
import static com.carenest.service.health.impl.HealthExamCatalog.find;
import static com.carenest.service.health.impl.HealthExamNormalizer.NOTE;
import static com.carenest.service.health.impl.HealthExamNormalizer.SHORT;
import static com.carenest.service.health.impl.HealthExamNormalizer.SPECIALTY;
import static com.carenest.service.health.impl.HealthExamNormalizer.TEXT;
import static com.carenest.service.health.impl.HealthExamNormalizer.isBlank;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthExamForm.HealthExamAdmin;
import com.carenest.dto.health.HealthExamForm.HealthExamConclusion;
import com.carenest.dto.health.HealthExamForm.HealthExamDevelopment;
import com.carenest.dto.health.HealthExamForm.HealthExamHistory;
import com.carenest.dto.health.HealthExamForm.HealthExamImmunization;
import com.carenest.dto.health.HealthExamForm.HealthExamNutrition;
import com.carenest.dto.health.HealthExamReference;
import com.carenest.service.health.impl.HealthExamCatalog.ChoiceItem;
import com.carenest.service.health.impl.HealthExamCatalog.HistoryItem;
import com.carenest.service.health.impl.HealthExamCatalog.Option;
import com.carenest.service.health.impl.HealthExamCatalog.VitalItem;
import com.carenest.utils.enums.HealthConclusionMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kiểm phiếu khám khi lưu kết luận (như {@code healthExamValidation.js} của FE): câu lỗi và khóa ô như FE để form mở đúng bước.
 * Mỗi ô một lỗi (luật hỏng đầu tiên), thứ tự như BE giả. Không kiểm trùng ngày đo với lần đo khác (service kiểm).
 */
final class HealthExamValidator {

    /**
     * {@code exam} đã chuẩn hóa ({@link HealthExamNormalizer}); số đo lấy theo body, chưa làm tròn.
     * {@code alreadyConcluded}: sửa phiếu đã kết luận ⇒ bắt buộc lý do. {@code hasKnownCondition}: trẻ có vấn đề đã
     * biết ⇒ mẫu STANDARD hỏi "vấn đề mới hay đã biết".
     */
    record Input(
            HealthExamForm exam,
            LocalDate date,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal headCircumferenceCm,
            String reason,
            boolean alreadyConcluded,
            HealthConclusionMode mode,
            HealthExamReference reference,
            LocalDate today,
            LocalDate dateOfBirth,
            LocalDate campaignStart,
            LocalDate campaignEnd,
            int guardianCount,
            boolean hasKnownCondition) {}

    private HealthExamValidator() {}

    static List<FieldErrorResponse> validate(Input in) {
        Map<String, String> errors = new LinkedHashMap<>();
        HealthExamForm exam = in.exam() == null ? HealthExamNormalizer.blank() : in.exam();
        HealthExamReference reference = in.reference();

        checkDate(errors, in);
        checkCompanion(errors, exam.admin(), in.guardianCount());
        checkHistory(errors, exam.history(), in);

        // II. Sinh tồn
        for (VitalItem item : VITAL_ITEMS) {
            checkNumber(
                    errors,
                    "exam.vitals." + item.key(),
                    item.value().apply(exam.vitals()),
                    true,
                    item.name().toLowerCase(Locale.ROOT),
                    item.min(),
                    item.max(),
                    item.unit());
            checkChoice(
                    errors,
                    "exam.vitals." + item.statusKey(),
                    item.status().apply(exam.vitals()),
                    item.options(),
                    "Chọn trạng thái");
        }

        // III. Dinh dưỡng; vòng đầu chỉ đo tới 60 tháng
        HealthExamNutrition n = exam.nutrition();
        checkNumber(errors, "heightCm", in.heightCm(), true, "chiều cao/chiều dài", 40, 150, "cm");
        checkNumber(errors, "weightKg", in.weightKg(), true, "cân nặng", 3, 60, "kg");
        if (reference == null || reference.headApplies()) {
            checkNumber(errors, "headCircumferenceCm", in.headCircumferenceCm(), true, "vòng đầu", 30, 60, "cm");
            checkChoice(
                    errors,
                    "exam.nutrition.headStatus",
                    n.headStatus(),
                    HEAD_STATUS_OPTIONS,
                    "Chọn trạng thái vòng đầu");
        }
        checkNumber(
                errors, "exam.nutrition.lengthForAgeSd", n.lengthForAgeSd(), false, "chiều dài/tuổi (SD)", -6, 6, "");
        checkNumber(
                errors, "exam.nutrition.weightForAgeSd", n.weightForAgeSd(), false, "cân nặng/tuổi (SD)", -6, 6, "");
        checkNumber(
                errors,
                "exam.nutrition.armCircumferenceMm",
                n.armCircumferenceMm(),
                false,
                "chu vi vòng cánh tay",
                50,
                300,
                "mm");
        checkStatuses(errors, n.statuses());

        // IV–V. Phát triển, tiêm chủng; câu tự kỷ chỉ hỏi trẻ 16–30 tháng
        boolean autismQuestion = reference != null && reference.autismQuestion();
        for (ChoiceItem<HealthExamDevelopment> item : DEVELOPMENT_ITEMS) {
            if (item.key().equals(AUTISM_RISK) && !autismQuestion) {
                continue;
            }
            checkChoice(
                    errors,
                    "exam.development." + item.key(),
                    item.value().apply(exam.development()),
                    item.options(),
                    "Chọn Có hoặc Không");
        }
        for (ChoiceItem<HealthExamImmunization> item : IMMUNIZATION_ITEMS) {
            checkChoice(
                    errors,
                    "exam.immunization." + item.key(),
                    item.value().apply(exam.immunization()),
                    item.options(),
                    "Chọn Có hoặc Không");
        }

        // VI. Khám lâm sàng
        Map<String, String> clinical = exam.clinical() == null ? Map.of() : exam.clinical();
        for (ChoiceItem<Map<String, String>> item : CLINICAL_ITEMS) {
            checkChoice(
                    errors,
                    "exam.clinical." + item.key(),
                    item.value().apply(clinical),
                    item.options(),
                    "Chọn một kết quả");
        }
        if (BEHAVIOR_NOTE_TRIGGERS.stream().anyMatch(k -> YES.equals(clinical.get(k)))
                && isBlank(clinical.get(BEHAVIOR_NOTE))) {
            errors.putIfAbsent("exam.clinical.behaviorNote", "Ghi rõ biểu hiện bác sĩ ghi nhận");
        }
        checkLength(errors, "exam.clinical.behaviorNote", clinical.get(BEHAVIOR_NOTE), TEXT);

        // Kết luận và tư vấn
        HealthExamConclusion c = exam.conclusion();
        if (in.mode() == HealthConclusionMode.FOLLOW_UP) {
            checkFollowUpConclusion(errors, c);
        } else {
            checkStandardConclusion(errors, c, in.hasKnownCondition());
        }
        if (isBlank(c.doctorName())) {
            errors.putIfAbsent("exam.conclusion.doctorName", "Nhập họ tên bác sĩ kết luận");
        } else {
            checkLength(errors, "exam.conclusion.doctorName", c.doctorName(), SHORT);
        }
        if (HealthExamNormalizer.needsParentOpinion(exam, in.mode())) {
            if (isBlank(c.parentOpinion())) {
                errors.putIfAbsent(
                        "exam.conclusion.parentOpinion", "Ghi ý kiến của phụ huynh (người đi cùng) về kết luận");
            } else {
                checkLength(errors, "exam.conclusion.parentOpinion", c.parentOpinion(), NOTE);
            }
        }

        // HLT-13: sửa phiếu đã kết luận phải ghi lý do
        if (in.alreadyConcluded() && isBlank(in.reason())) {
            errors.putIfAbsent("reason", "Nhập lý do sửa phiếu đã có kết luận");
        } else {
            checkLength(errors, "reason", in.reason(), TEXT);
        }

        return errors.entrySet().stream()
                .map(e -> new FieldErrorResponse(e.getKey(), e.getValue()))
                .toList();
    }

    private static void checkDate(Map<String, String> errors, Input in) {
        LocalDate date = in.date();
        if (date == null) {
            errors.putIfAbsent("date", "Chọn ngày khám");
        } else if (in.today() != null && date.isAfter(in.today())) {
            errors.putIfAbsent("date", "Ngày khám không được ở tương lai");
        } else if (in.dateOfBirth() != null && date.isBefore(in.dateOfBirth())) {
            errors.putIfAbsent("date", "Ngày khám không được trước ngày sinh của trẻ");
        } else if ((in.campaignStart() != null && date.isBefore(in.campaignStart()))
                || (in.campaignEnd() != null && date.isAfter(in.campaignEnd()))) {
            errors.putIfAbsent("date", "Chọn ngày khám trong khoảng ngày khám của đợt");
        }
    }

    // Người đi cùng chọn trong danh sách phụ huynh của hồ sơ trẻ (guardianIndex)
    private static void checkCompanion(Map<String, String> errors, HealthExamAdmin admin, int guardianCount) {
        String hasCompanion = admin.hasCompanion();
        if (!HealthExamCatalog.isOption("exam.admin.hasCompanion", hasCompanion)) {
            errors.putIfAbsent("exam.admin.hasCompanion", "Chọn có người đi cùng trẻ hay không");
        } else if (YES.equals(hasCompanion)) {
            Integer index = admin.companion() == null ? null : admin.companion().guardianIndex();
            if (index == null || index < 0 || index >= guardianCount) {
                errors.putIfAbsent(
                        "exam.admin.companion", "Chọn người đi cùng trong danh sách phụ huynh của hồ sơ trẻ");
            }
        }
    }

    private static void checkHistory(Map<String, String> errors, HealthExamHistory history, Input in) {
        for (HistoryItem item : HISTORY_ITEMS) {
            String answer = item.answer().apply(history);
            checkChoice(
                    errors,
                    "exam.history." + item.key(),
                    answer,
                    NO_YES,
                    "Chọn " + item.label().toLowerCase(Locale.ROOT));
            if (item.textKey() != null) {
                String text = item.text().apply(history);
                if (YES.equals(answer) && isBlank(text)) {
                    errors.putIfAbsent("exam.history." + item.textKey(), "Ghi rõ tên bệnh");
                }
                checkLength(errors, "exam.history." + item.textKey(), text, TEXT);
            }
            if (item.dateKey() != null && YES.equals(answer)) {
                String error = tbContactDateError(history.tbContactDate(), in);
                if (error != null) {
                    errors.putIfAbsent("exam.history." + item.dateKey(), error);
                }
            }
        }
    }

    // Ngày tiếp xúc lao gần nhất: không ở tương lai, không sau ngày khám, không trước ngày sinh
    private static String tbContactDateError(LocalDate value, Input in) {
        if (value == null) {
            return "Chọn ngày tiếp xúc gần nhất";
        }
        if (in.today() != null && value.isAfter(in.today())) {
            return "Ngày tiếp xúc không được ở tương lai";
        }
        if (in.date() != null && value.isAfter(in.date())) {
            return "Ngày tiếp xúc không được sau ngày khám " + HealthExamCatalog.formatDate(in.date());
        }
        if (in.dateOfBirth() != null && value.isBefore(in.dateOfBirth())) {
            return "Ngày tiếp xúc không được trước ngày sinh của trẻ";
        }
        return null;
    }

    private static void checkStatuses(Map<String, String> errors, List<String> statuses) {
        List<String> values = statuses == null ? List.of() : statuses;
        String key = "exam.nutrition.statuses";
        if (values.isEmpty()) {
            errors.putIfAbsent(key, "Chọn tình trạng dinh dưỡng");
        } else if (values.stream().anyMatch(s -> find(NUTRITION_STATUS_OPTIONS, s) == null)) {
            errors.putIfAbsent(key, "Chọn tình trạng dinh dưỡng hợp lệ");
        } else if (values.contains(NORMAL) && values.size() > 1) {
            errors.putIfAbsent(key, "\"Bình thường\" không chọn cùng các mục khác");
        }
    }

    // Mẫu FOLLOW_UP: 1 câu so với vấn đề đang theo dõi
    private static void checkFollowUpConclusion(Map<String, String> errors, HealthExamConclusion c) {
        String finding = c.followUpFinding();
        if (find(FOLLOW_UP_FINDING_OPTIONS, finding) == null) {
            errors.putIfAbsent(
                    "exam.conclusion.followUpFinding", "Chọn kết luận của bác sĩ so với vấn đề đang theo dõi");
        } else if (c.tbRisk() && FINDING_NO_NEW.equals(finding)) {
            // Nguy cơ lao tính như bất thường mới
            errors.putIfAbsent(
                    "exam.conclusion.followUpFinding",
                    "Đã tích nguy cơ lao: chọn \"Tư vấn\" hoặc \"Có thêm bất thường mới\"");
        }
        if (FINDING_ADVICE.equals(finding)) {
            if (isBlank(c.issueText())) {
                errors.putIfAbsent("exam.conclusion.issueText", "Ghi rõ vấn đề bác sĩ ghi nhận");
            }
            if (isBlank(c.adviceText())) {
                errors.putIfAbsent("exam.conclusion.adviceText", "Ghi lời tư vấn của bác sĩ");
            }
        }
        if (FINDING_NEW.equals(finding) && isBlank(c.issueText())) {
            errors.putIfAbsent("exam.conclusion.issueText", "Ghi rõ bất thường mới bác sĩ ghi nhận");
        }
        checkLength(errors, "exam.conclusion.issueText", c.issueText(), NOTE);
        checkLength(errors, "exam.conclusion.adviceText", c.adviceText(), NOTE);
        checkLength(errors, "exam.conclusion.specialty", c.specialty(), SPECIALTY);
    }

    private static void checkStandardConclusion(
            Map<String, String> errors, HealthExamConclusion c, boolean hasKnownCondition) {
        if (!c.normal() && !c.tbRisk() && !c.healthIssue()) {
            errors.putIfAbsent("exam.conclusion.flags", "Chọn kết luận của bác sĩ");
        } else if (c.normal() && (c.tbRisk() || c.healthIssue())) {
            errors.putIfAbsent("exam.conclusion.flags", "\"Bình thường\" không chọn cùng các kết luận khác");
        }
        if (c.healthIssue() && isBlank(c.issueText())) {
            errors.putIfAbsent("exam.conclusion.issueText", "Ghi rõ vấn đề sức khỏe bác sĩ kết luận");
        }
        checkLength(errors, "exam.conclusion.issueText", c.issueText(), NOTE);
        String action = c.action();
        if (find(ACTION_OPTIONS, action) == null) {
            errors.putIfAbsent("exam.conclusion.action", "Chọn hướng xử lý bác sĩ đề nghị");
        } else if ((c.tbRisk() || c.healthIssue()) && ACTION_NONE.equals(action)) {
            errors.putIfAbsent(
                    "exam.conclusion.action",
                    "Có nguy cơ lao hoặc có vấn đề sức khỏe: chọn Tư vấn hoặc Chuyển khám chuyên khoa");
        }
        // Trẻ có vấn đề đã biết, lần này có vấn đề mà không chuyển khám, không nguy cơ lao: bác sĩ nói mới hay đã biết
        if (hasKnownCondition
                && c.healthIssue()
                && !c.tbRisk()
                && !ACTION_REFERRAL.equals(action)
                && find(ISSUE_KIND_OPTIONS, c.issueKind()) == null) {
            errors.putIfAbsent("exam.conclusion.issueKind", "Chọn vấn đề mới hay vấn đề đã biết");
        }
        if (ACTION_ADVICE.equals(action) && isBlank(c.adviceText())) {
            errors.putIfAbsent("exam.conclusion.adviceText", "Ghi lời tư vấn của bác sĩ");
        }
        checkLength(errors, "exam.conclusion.adviceText", c.adviceText(), NOTE);
        if (ACTION_REFERRAL.equals(action) && isBlank(c.specialty())) {
            errors.putIfAbsent("exam.conclusion.specialty", "Ghi chuyên khoa bác sĩ đề nghị, ví dụ Mắt");
        }
        checkLength(errors, "exam.conclusion.specialty", c.specialty(), SPECIALTY);
    }

    // So khoảng trên số đã làm tròn 0,1 như lúc lưu (số trong phiếu đã chuẩn hóa không đổi); quá ±10^9 ⇒ ngoài khoảng
    private static void checkNumber(
            Map<String, String> errors,
            String key,
            BigDecimal value,
            boolean required,
            String label,
            int min,
            int max,
            String unit) {
        if (value == null) {
            if (required) {
                errors.putIfAbsent(key, "Nhập " + label);
            }
            return;
        }
        BigDecimal rounded = HealthExamNormalizer.round(value, 1);
        if (rounded == null
                || rounded.compareTo(BigDecimal.valueOf(min)) < 0
                || rounded.compareTo(BigDecimal.valueOf(max)) > 0) {
            errors.putIfAbsent(
                    key, "Nhập " + label + " từ " + min + " đến " + max + (unit.isEmpty() ? "" : " " + unit));
        }
    }

    private static void checkChoice(
            Map<String, String> errors, String key, String value, List<Option> options, String message) {
        if (find(options, value) == null) {
            errors.putIfAbsent(key, message);
        }
    }

    // Chữ trong phiếu đã cắt theo giới hạn khi chuẩn hóa nên thực tế chỉ còn lý do sửa có thể quá dài
    private static void checkLength(Map<String, String> errors, String key, String value, int max) {
        if (HealthExamNormalizer.trim(value).length() > max) {
            errors.putIfAbsent(key, "Tối đa " + max + " ký tự");
        }
    }
}
