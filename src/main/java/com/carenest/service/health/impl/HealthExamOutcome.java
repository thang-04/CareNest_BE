package com.carenest.service.health.impl;

import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.utils.enums.HealthConclusionCategory;
import com.carenest.utils.enums.HealthExamStatus;
import com.carenest.utils.enums.HealthFollowUpLevel;
import java.util.ArrayList;
import java.util.List;

/**
 * Mức theo dõi, nhóm kết luận, kết luận rút gọn của một lần đo, chỉ từ kết luận bác sĩ của phiếu khám đã kết luận (HLT-10,
 * user chốt 2026-10-31; phân loại WHO hoãn). Số đo của kỳ cân đo và phiếu nháp chưa có mức.
 */
final class HealthExamOutcome {

    static final String YES = "YES";
    static final String FINDING_NEW = "NEW_FINDING";
    static final String FINDING_ADVICE = "ADVICE";
    static final String FINDING_NONE = "NO_NEW_FINDING";
    static final String ACTION_REFERRAL = "REFERRAL";
    static final String ISSUE_KNOWN = "KNOWN";
    // Giới hạn ghi chú của dòng thời gian phiếu theo dõi
    private static final int NOTE_MAX = 500;

    record Outcome(HealthFollowUpLevel level, HealthConclusionCategory category) {}

    static final Outcome UNAVAILABLE = new Outcome(HealthFollowUpLevel.UNAVAILABLE, null);

    private HealthExamOutcome() {}

    /** {@code exam}: nội dung phiếu đã đọc JSON, null khi lần đo không có phiếu khám. Điều kiện xét từ trên xuống. */
    static Outcome of(HealthMeasurement m, HealthExamForm exam) {
        if (m.getExamStatus() != HealthExamStatus.CONCLUDED || exam == null || exam.conclusion() == null) {
            return UNAVAILABLE;
        }
        HealthExamForm.HealthExamConclusion c = exam.conclusion();
        if (c.tbRisk()) {
            return new Outcome(HealthFollowUpLevel.CLOSE_WATCH, HealthConclusionCategory.TB_RISK);
        }
        switch (text(c.followUpFinding())) {
            case FINDING_NEW -> {
                return new Outcome(HealthFollowUpLevel.CLOSE_WATCH, HealthConclusionCategory.FOLLOWING_NEW);
            }
            case FINDING_ADVICE -> {
                return new Outcome(HealthFollowUpLevel.CLOSE_WATCH, HealthConclusionCategory.ISSUE);
            }
            case FINDING_NONE -> {
                return new Outcome(HealthFollowUpLevel.CLOSE_WATCH, HealthConclusionCategory.FOLLOWING_NO_NEW);
            }
            default -> {
                // Mẫu kết luận thường, xét tiếp bên dưới
            }
        }
        if (ACTION_REFERRAL.equals(c.action())) {
            return new Outcome(HealthFollowUpLevel.CLOSE_WATCH, HealthConclusionCategory.REFERRAL);
        }
        if (c.healthIssue()) {
            return new Outcome(
                    ISSUE_KNOWN.equals(c.issueKind()) ? HealthFollowUpLevel.KNOWN_STABLE : HealthFollowUpLevel.WATCH,
                    HealthConclusionCategory.ISSUE);
        }
        return new Outcome(HealthFollowUpLevel.NONE, HealthConclusionCategory.NORMAL);
    }

    /** Kết luận rút gọn cho danh sách, phiếu theo dõi, lần khám sau; null khi không phải phiếu đã kết luận. */
    static HealthMeasurementResponse.HealthConclusionBrief brief(HealthMeasurement m, HealthExamForm exam) {
        if (m == null || m.getExamStatus() != HealthExamStatus.CONCLUDED || exam == null || exam.conclusion() == null) {
            return null;
        }
        HealthExamForm.HealthExamConclusion c = exam.conclusion();
        return new HealthMeasurementResponse.HealthConclusionBrief(
                text(c.doctorName()),
                text(c.issueText()),
                c.tbRisk(),
                text(c.action()),
                text(c.adviceText()),
                text(c.specialty()),
                text(c.issueKind()),
                text(c.followUpFinding()));
    }

    /** Người đi cùng đã nghe bác sĩ kết luận tại buổi khám; null khi không có. */
    static HealthExamForm.HealthExamCompanion companionOf(HealthExamForm exam) {
        HealthExamForm.HealthExamAdmin admin = exam == null ? null : exam.admin();
        if (admin == null || !YES.equals(admin.hasCompanion()) || admin.companion() == null) {
            return null;
        }
        return text(admin.companion().fullName()).isBlank() ? null : admin.companion();
    }

    /** Khám theo mẫu FOLLOW_UP (bác sĩ kết luận so với vấn đề đang theo dõi). */
    static boolean followUpMode(HealthExamForm exam) {
        return !text(exam.conclusion().followUpFinding()).isEmpty();
    }

    /** Có bất thường mới (mở vòng trao đổi): mẫu thường luôn có; mẫu theo dõi khi có bất thường mới hoặc nguy cơ lao. */
    static boolean newFinding(HealthExamForm exam) {
        HealthExamForm.HealthExamConclusion c = exam.conclusion();
        return !followUpMode(exam) || FINDING_NEW.equals(c.followUpFinding()) || c.tbRisk();
    }

    /** Ghi chú trao đổi tại buổi khám; ý kiến phụ huynh đặt thứ hai để không bị cắt khi ghi chú dài. */
    static String contactNote(HealthExamForm exam, HealthExamForm.HealthExamCompanion companion) {
        HealthExamForm.HealthExamConclusion c = exam.conclusion();
        String relation = text(companion.relation()).isEmpty() ? "người đi cùng" : companion.relation();
        List<String> parts = new ArrayList<>();
        parts.add("Bác sĩ trao đổi kết luận với " + text(companion.fullName()) + " (" + relation + ") tại buổi khám.");
        if (!text(c.parentOpinion()).isEmpty()) {
            parts.add("Ý kiến phụ huynh: " + sentence(c.parentOpinion()));
        }
        if (c.tbRisk()) {
            parts.add("Kết luận có nguy cơ mắc lao (tiền sử tiếp xúc).");
        }
        if (FINDING_NEW.equals(c.followUpFinding()) && !text(c.issueText()).isEmpty()) {
            parts.add("Bất thường mới: " + sentence(c.issueText()));
        }
        if (!text(c.specialty()).isEmpty()
                && (ACTION_REFERRAL.equals(c.action())
                        || !text(c.followUpFinding()).isEmpty())) {
            parts.add("Đề nghị khám chuyên khoa: " + text(c.specialty()) + ".");
        }
        if (!text(c.adviceText()).isEmpty()) {
            parts.add("Lời dặn: " + text(c.adviceText()));
        }
        String note = String.join(" ", parts);
        return note.length() > NOTE_MAX ? note.substring(0, NOTE_MAX) : note;
    }

    // Câu kết thúc bằng dấu chấm (thay dấu . ! ? cuối nếu có)
    private static String sentence(String value) {
        String t = text(value);
        return (t.endsWith(".") || t.endsWith("!") || t.endsWith("?") ? t.substring(0, t.length() - 1) : t) + ".";
    }

    static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
