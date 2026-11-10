package com.carenest.service.health.impl;

import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthMeasurementResponse;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.utils.enums.HealthConclusionCategory;
import com.carenest.utils.enums.HealthExamStatus;
import com.carenest.utils.enums.HealthFollowUpLevel;

/**
 * Mức theo dõi, nhóm kết luận, kết luận rút gọn của một lần đo, chỉ từ kết luận bác sĩ của phiếu khám đã kết luận (HLT-10,
 * user chốt 2026-10-31). Số đo của kỳ cân đo và phiếu nháp chưa có mức; kỳ cân đo cảnh báo bằng mức BMI (HLT-18).
 */
final class HealthExamOutcome {

    static final String FINDING_NEW = "NEW_FINDING";
    static final String FINDING_ADVICE = "ADVICE";
    static final String FINDING_NONE = "NO_NEW_FINDING";
    static final String ACTION_REFERRAL = "REFERRAL";
    static final String ISSUE_KNOWN = "KNOWN";

    record Outcome(HealthFollowUpLevel level, HealthConclusionCategory category) {}

    static final Outcome UNAVAILABLE = new Outcome(HealthFollowUpLevel.UNAVAILABLE, null);

    private HealthExamOutcome() {}

    /**
     * {@code exam}: nội dung phiếu đã đọc JSON, null khi lần đo không có phiếu khám. Điều kiện xét từ trên xuống; mẫu
     * FOLLOW_UP chỉ còn ở phiếu cũ (phiếu theo dõi đã bỏ — HLT-16).
     */
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

    /** Kết luận rút gọn cho danh sách, sổ sức khỏe, lần khám sau; null khi không phải phiếu đã kết luận. */
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

    /** Phiếu kết luận theo mẫu FOLLOW_UP cũ (bác sĩ kết luận so với vấn đề đang theo dõi). */
    static boolean followUpMode(HealthExamForm exam) {
        return !text(exam.conclusion().followUpFinding()).isEmpty();
    }

    static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
