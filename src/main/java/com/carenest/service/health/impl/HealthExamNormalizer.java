package com.carenest.service.health.impl;

import static com.carenest.service.health.impl.HealthExamCatalog.ACTION_REFERRAL;
import static com.carenest.service.health.impl.HealthExamCatalog.BEHAVIOR_NOTE;
import static com.carenest.service.health.impl.HealthExamCatalog.CLINICAL_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.FINDING_ADVICE;
import static com.carenest.service.health.impl.HealthExamCatalog.FINDING_NEW;
import static com.carenest.service.health.impl.HealthExamCatalog.NO;
import static com.carenest.service.health.impl.HealthExamCatalog.YES;

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
import com.carenest.dto.health.HealthExamReference;
import com.carenest.utils.enums.HealthConclusionMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Làm sạch phiếu khám trước khi lưu nháp hoặc kết luận (như {@code cleanExam} của mock FE): chỉ giữ ô của mẫu, chữ
 * cắt khoảng trắng và giới hạn độ dài, mã ngoài danh mục ⇒ {@code ""}, số làm tròn HALF_UP, bỏ ô không hỏi theo tuổi
 * và theo mẫu kết luận. Hành chính theo hồ sơ trẻ và người đi cùng theo phụ huynh do service điền sau.
 */
final class HealthExamNormalizer {

    static final int SHORT = 100;
    static final int TEXT = 300;
    static final int NOTE = 500;
    static final int ID_NUMBER = 20;
    static final int SPECIALTY = 150;

    // |số| ≥ 10^9 không phải số đo: coi như trống, cũng tránh setScale số mũ khổng lồ (tốn CPU)
    private static final BigDecimal MAX_ABS = BigDecimal.TEN.pow(9);

    private HealthExamNormalizer() {}

    /**
     * {@code raw} null hoặc thiếu phần/ô ⇒ ô trống ({@code ""}, null, false, danh sách rỗng), không lấy giá trị mặc
     * định của phiếu mới. {@code hasKnownCondition}: trẻ có vấn đề đã biết (mẫu STANDARD hỏi "mới hay đã biết").
     */
    static HealthExamForm normalize(
            HealthExamForm raw, HealthConclusionMode mode, HealthExamReference reference, boolean hasKnownCondition) {
        HealthExamForm r = raw == null ? new HealthExamForm(null, null, null, null, null, null, null, null, null) : raw;
        boolean headApplies = reference == null || reference.headApplies();
        boolean autismQuestion = reference != null && reference.autismQuestion();
        HealthExamAdmin admin = admin(r.admin());
        return new HealthExamForm(
                r.step() == null ? 0 : Math.clamp(r.step(), 0, 4),
                admin,
                history(r.history()),
                vitals(r.vitals()),
                nutrition(r.nutrition(), headApplies),
                development(r.development(), autismQuestion),
                immunization(r.immunization()),
                clinical(r.clinical()),
                conclusion(r.conclusion(), mode, hasKnownCondition, admin.hasCompanion()));
    }

    /**
     * Ý kiến phụ huynh tại buổi khám: có người đi cùng và kết luận ở mức "Cần theo dõi sát" (mẫu FOLLOW_UP,
     * nguy cơ lao hoặc chuyển khám chuyên khoa).
     */
    static boolean needsParentOpinion(HealthExamForm exam, HealthConclusionMode mode) {
        if (exam == null || exam.admin() == null) {
            return false;
        }
        HealthExamConclusion c = exam.conclusion();
        return needsOpinion(exam.admin().hasCompanion(), mode, c != null && c.tbRisk(), c == null ? null : c.action());
    }

    /** Phiếu trống hoàn toàn (mọi ô trống, kể cả phần VI), để đọc thay phiếu null. */
    static HealthExamForm blank() {
        return normalize(null, HealthConclusionMode.STANDARD, null, false);
    }

    /** Làm tròn HALF_UP như lúc lưu; null hoặc ngoài ±10^9 ⇒ null. */
    static BigDecimal round(BigDecimal value, int scale) {
        if (value == null || value.abs().compareTo(MAX_ABS) >= 0) {
            return null;
        }
        // Nhỏ hơn 10^-(scale+1) thì tròn về 0, không cần chia cho 10^n lớn
        if (value.precision() - value.scale() < -scale) {
            return BigDecimal.ZERO.setScale(scale);
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    /** Cắt khoảng trắng hai đầu như {@code String.prototype.trim} của JS (gồm NBSP, BOM); null ⇒ {@code ""}. */
    static String trim(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    static boolean isBlank(String value) {
        return trim(value).isEmpty();
    }

    /** Cắt khoảng trắng rồi giữ tối đa {@code max} ký tự, không tách đôi cặp surrogate. */
    static String text(String value, int max) {
        String s = trim(value);
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Character.isHighSurrogate(s.charAt(max - 1)) ? max - 1 : max);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '﻿';
    }

    // Số định danh: giữ 20 ký tự rồi bỏ mọi khoảng trắng
    private static String idNumber(String value) {
        String s = text(value, ID_NUMBER);
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (!isSpace(s.charAt(i))) {
                out.append(s.charAt(i));
            }
        }
        return out.toString();
    }

    private static String choice(String field, String code) {
        String value = trim(code);
        return HealthExamCatalog.isOption(field, value) ? value : "";
    }

    private static boolean needsOpinion(String hasCompanion, HealthConclusionMode mode, boolean tbRisk, String action) {
        boolean closeWatch = mode == HealthConclusionMode.FOLLOW_UP || tbRisk || ACTION_REFERRAL.equals(action);
        return YES.equals(hasCompanion) && closeWatch;
    }

    private static HealthExamAdmin admin(HealthExamAdmin raw) {
        HealthExamAdmin a =
                raw == null ? new HealthExamAdmin(null, null, null, null, null, null, null, null, null, null) : raw;
        HealthExamAddress address = a.address() == null ? new HealthExamAddress(null, null, null) : a.address();
        String hasCompanion = choice("exam.admin.hasCompanion", a.hasCompanion());
        HealthExamCompanion c = a.companion();
        HealthExamCompanion companion = c == null || NO.equals(hasCompanion)
                ? HealthExamCatalog.blankCompanion()
                : new HealthExamCompanion(
                        c.guardianIndex(),
                        text(c.fullName(), SHORT),
                        text(c.relation(), SHORT),
                        text(c.phone(), SHORT),
                        idNumber(c.idNumber()));
        return new HealthExamAdmin(
                idNumber(a.childIdNumber()),
                choice("exam.admin.premature", a.premature()),
                a.gestationWeeks(),
                text(a.ethnicity(), SHORT),
                text(a.beneficiary(), SHORT),
                text(a.paymentSource(), SHORT),
                text(a.bloodType(), SHORT),
                new HealthExamAddress(
                        text(address.province(), SHORT), text(address.ward(), SHORT), text(address.street(), TEXT)),
                hasCompanion,
                companion);
    }

    // Tên bệnh không xóa khi trả lời "Không" (giữ như phiếu gửi)
    private static HealthExamHistory history(HealthExamHistory raw) {
        HealthExamHistory h = raw == null ? new HealthExamHistory(null, null, null, null, null, null) : raw;
        return new HealthExamHistory(
                choice("exam.history.self", h.self()),
                text(h.selfText(), TEXT),
                choice("exam.history.family", h.family()),
                text(h.familyText(), TEXT),
                choice("exam.history.tbContact", h.tbContact()),
                h.tbContactDate());
    }

    private static HealthExamVitals vitals(HealthExamVitals raw) {
        HealthExamVitals v = raw == null ? new HealthExamVitals(null, null, null, null, null, null) : raw;
        return new HealthExamVitals(
                round(v.temperatureC(), 1),
                choice("exam.vitals.temperatureStatus", v.temperatureStatus()),
                round(v.pulse(), 0),
                choice("exam.vitals.pulseStatus", v.pulseStatus()),
                round(v.respiratoryRate(), 0),
                choice("exam.vitals.respiratoryStatus", v.respiratoryStatus()));
    }

    private static HealthExamNutrition nutrition(HealthExamNutrition raw, boolean headApplies) {
        HealthExamNutrition n = raw == null ? new HealthExamNutrition(null, null, null, null, null) : raw;
        Set<String> statuses = new LinkedHashSet<>();
        if (n.statuses() != null) {
            for (String status : n.statuses()) {
                String code = choice("exam.nutrition.statuses", status);
                if (!code.isEmpty()) {
                    statuses.add(code);
                }
            }
        }
        return new HealthExamNutrition(
                round(n.lengthForAgeSd(), 1),
                round(n.weightForAgeSd(), 1),
                // Trẻ quá 60 tháng không đo vòng đầu
                headApplies ? choice("exam.nutrition.headStatus", n.headStatus()) : "",
                round(n.armCircumferenceMm(), 0),
                List.copyOf(statuses));
    }

    private static HealthExamDevelopment development(HealthExamDevelopment raw, boolean autismQuestion) {
        HealthExamDevelopment d = raw == null ? new HealthExamDevelopment(null, null, null) : raw;
        return new HealthExamDevelopment(
                choice("exam.development.mental", d.mental()),
                choice("exam.development.motor", d.motor()),
                autismQuestion ? choice("exam.development.autismRisk", d.autismRisk()) : "");
    }

    private static HealthExamImmunization immunization(HealthExamImmunization raw) {
        HealthExamImmunization i = raw == null ? new HealthExamImmunization(null, null, null) : raw;
        return new HealthExamImmunization(
                choice("exam.immunization.bcg", i.bcg()),
                choice("exam.immunization.hepB1", i.hepB1()),
                choice("exam.immunization.fullSchedule", i.fullSchedule()));
    }

    // Mục thiếu ⇒ "" (không lấy mặc định của phiếu mới)
    private static Map<String, String> clinical(Map<String, String> raw) {
        Map<String, String> values = raw == null ? Map.of() : raw;
        Map<String, String> out = new LinkedHashMap<>();
        CLINICAL_ITEMS.forEach(
                item -> out.put(item.key(), choice("exam.clinical." + item.key(), values.get(item.key()))));
        out.put(BEHAVIOR_NOTE, text(values.get(BEHAVIOR_NOTE), TEXT));
        return out;
    }

    /** Kết luận chỉ giữ ô của mẫu đang dùng. */
    private static HealthExamConclusion conclusion(
            HealthExamConclusion raw, HealthConclusionMode mode, boolean hasKnownCondition, String hasCompanion) {
        HealthExamConclusion c = raw == null
                ? new HealthExamConclusion(false, false, false, null, null, null, null, null, null, null, null)
                : raw;
        boolean tbRisk = c.tbRisk();
        String issueText = text(c.issueText(), NOTE);
        String adviceText = text(c.adviceText(), NOTE);
        String specialty = text(c.specialty(), SPECIALTY);
        String doctorName = text(c.doctorName(), SHORT);
        String parentOpinion = text(c.parentOpinion(), NOTE);
        if (mode == HealthConclusionMode.FOLLOW_UP) {
            String finding = choice("exam.conclusion.followUpFinding", c.followUpFinding());
            boolean keepsIssue = finding.equals(FINDING_ADVICE) || finding.equals(FINDING_NEW);
            return new HealthExamConclusion(
                    false,
                    tbRisk,
                    false,
                    "",
                    finding,
                    "",
                    keepsIssue ? issueText : "",
                    adviceText,
                    finding.equals(FINDING_NEW) ? specialty : "",
                    doctorName,
                    needsOpinion(hasCompanion, mode, tbRisk, "") ? parentOpinion : "");
        }
        String action = choice("exam.conclusion.action", c.action());
        // "Mới hay đã biết" chỉ hỏi trẻ có vấn đề đã biết, lần này có vấn đề, không chuyển khám, không nguy cơ lao
        boolean asksKind = hasKnownCondition && c.healthIssue() && !tbRisk && !ACTION_REFERRAL.equals(action);
        return new HealthExamConclusion(
                c.normal(),
                tbRisk,
                c.healthIssue(),
                asksKind ? choice("exam.conclusion.issueKind", c.issueKind()) : "",
                "",
                action,
                issueText,
                adviceText,
                specialty,
                doctorName,
                needsOpinion(hasCompanion, mode, tbRisk, action) ? parentOpinion : "");
    }
}
