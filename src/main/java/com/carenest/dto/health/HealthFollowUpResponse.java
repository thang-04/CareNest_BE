package com.carenest.dto.health;

import com.carenest.utils.enums.HealthFollowUpAction;
import com.carenest.utils.enums.HealthFollowUpLevel;
import com.carenest.utils.enums.HealthFollowUpOutcome;
import com.carenest.utils.enums.HealthFollowUpStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Phiếu theo dõi sức khỏe. Trạng thái, vòng trao đổi hiện tại ({@code currentRound}: hạn trao đổi 7 ngày từ lúc lưu kết
 * quả mở vòng, số ngày quá hạn) tính khi đọc. {@code latestMeasurement}, {@code timeline}, {@code measurements} chỉ có ở
 * chi tiết (null ở danh sách).
 *
 * @param closeOutcomeOptions các kết quả đóng chọn được lúc này; rỗng khi không cập nhật được
 * @param canRecordExternal ghi được kết quả chuyên khoa: cập nhật được và vòng hiện tại đã trao đổi phụ huynh
 */
public record HealthFollowUpResponse(
        Long id,
        HealthFollowUpStatus status,
        Instant openedAt,
        Instant updatedAt,
        Instant closedAt,
        String closedByName,
        String closeReason,
        HealthFollowUpOutcome closeOutcome,
        String closeConditionText,
        List<HealthFollowUpOutcome> closeOutcomeOptions,
        HealthFollowUpChild child,
        HealthFollowUpMeasurement latest,
        boolean tbRisk,
        HealthReferral referral,
        HealthFollowUpRound currentRound,
        HealthExternalResultBrief latestExternalResult,
        boolean canUpdate,
        boolean canRecordExternal,
        HealthFollowUpMeasurement latestMeasurement,
        List<HealthFollowUpEventView> timeline,
        List<HealthMeasurementResponse> measurements) {

    /** Trẻ của phiếu: lớp, điểm trường đang học (null khi không còn lớp). */
    public record HealthFollowUpChild(
            Long id, String fullName, String code, Long classId, String className, Long campusId, String campusName) {}

    /** Một lần khám tóm tắt; {@code conclusion} null khi không phải phiếu khám đã kết luận. */
    public record HealthFollowUpMeasurement(
            Long measurementId,
            LocalDate date,
            String campaignName,
            HealthFollowUpLevel followUpLevel,
            String source,
            HealthMeasurementResponse.HealthConclusionBrief conclusion) {}

    public record HealthFollowUpRound(
            Instant startedAt, LocalDate contactFrom, LocalDate dueOn, boolean contacted, int overdueDays) {}

    /** Đề nghị gần nhất trong phiếu: lần khám đã kết luận mới nhất, bỏ lần khám chỉ ghi nhận (không bất thường mới). */
    public record HealthReferral(
            LocalDate date,
            Long measurementId,
            String doctorName,
            String issueText,
            boolean tbRisk,
            String action,
            String adviceText,
            String specialty,
            String issueKind,
            String followUpFinding) {}

    /**
     * Tóm tắt phiếu cho Sổ sức khỏe, danh sách lớp của đợt, phiếu khám. {@code openedOn}: ngày khám mở phiếu; {@code
     * dueOn}, {@code overdueDays}: hạn trao đổi của vòng hiện tại (null/0 khi đã đóng).
     */
    public record HealthFollowUpBrief(
            Long id,
            HealthFollowUpStatus status,
            Instant openedAt,
            LocalDate openedOn,
            LocalDate dueOn,
            int overdueDays,
            HealthExternalResultBrief latestExternalResult,
            boolean tbRisk,
            HealthReferral referral) {}

    /** Kết quả chuyên khoa gần nhất (theo ngày khám); {@code byName} chỉ có ở phiếu theo dõi. */
    public record HealthExternalResultBrief(
            LocalDate on, String note, String facility, LocalDate nextVisitOn, String byName) {}

    /** Ảnh kết quả chuyên khoa; tải bằng {@code GET /health-follow-ups/{followUpId}/images/{id}}. */
    public record HealthFollowUpImageView(Long id, String path) {}

    /**
     * Một sự kiện trên dòng thời gian. {@code examOn}: ngày khám (OPENED, MEASUREMENT_ADDED); {@code on}: ngày trao đổi,
     * ngày khám chuyên khoa; {@code newRound}: lần khám mở vòng trao đổi mới; {@code auto}: hệ thống tự đóng.
     */
    public record HealthFollowUpEventView(
            Long id,
            HealthFollowUpAction action,
            Instant at,
            Long userId,
            String userName,
            Long measurementId,
            LocalDate examOn,
            Boolean newFinding,
            boolean newRound,
            LocalDate on,
            String note,
            boolean atExam,
            String facility,
            List<HealthFollowUpImageView> images,
            LocalDate nextVisitOn,
            HealthFollowUpOutcome outcome,
            String conditionText,
            String auto,
            HealthFollowUpMeasurement measurement) {}
}
