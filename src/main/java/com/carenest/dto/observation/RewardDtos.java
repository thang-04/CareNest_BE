package com.carenest.dto.observation;

import com.carenest.utils.enums.RewardAction;
import com.carenest.utils.enums.RewardStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Đề xuất khen thưởng cuối năm theo danh sách lớp. */
public final class RewardDtos {

    private RewardDtos() {}

    /** Ý kiến PHT (decision FORWARD / RETURN) hoặc HT (APPROVE / RETURN). */
    public record Step(Long by, String byName, Instant at, String decision, String comment) {}

    public record HistoryRow(Long id, RewardAction action, Long userId, String userName, Instant at, String note) {}

    /** status null: lớp chưa lập danh sách. awarded: số trẻ có danh hiệu (null khi PHT / HT xem bản nháp). */
    public record RewardClassRow(
            Long classId,
            String className,
            Long campusId,
            Long listId,
            RewardStatus status,
            int children,
            Integer awarded,
            Instant submittedAt,
            Instant updatedAt) {}

    public record YearEndRef(Long id, String finalContent, Instant confirmedAt) {}

    /**
     * Số liệu cả năm của trẻ: ngày học của lớp (thứ 2–6 có điểm danh), ngày có mặt, phiếu bé ngoan đã phát; suggested:
     * danh hiệu hệ thống gợi ý; titles / note: GV đã chọn.
     */
    public record RewardChildRow(
            Long childId,
            String fullName,
            LocalDate dateOfBirth,
            int schoolDays,
            int presentDays,
            Integer attendanceRate,
            int weeklyTickets,
            int monthlyTickets,
            YearEndRef yearEnd,
            Boolean programCompleted,
            List<String> suggested,
            List<String> titles,
            String note) {}

    public record RewardClassDetail(
            Long listId,
            Long classId,
            String className,
            Long campusId,
            Long schoolYearId,
            boolean programClass,
            RewardStatus status,
            Long createdBy,
            String createdByName,
            Long updatedBy,
            String updatedByName,
            Instant submittedAt,
            Step vpReview,
            Step principalDecision,
            List<String> titleOptions,
            List<RewardChildRow> children,
            List<HistoryRow> history,
            boolean canEdit,
            boolean canReview,
            boolean canDecide) {}

    public record RewardItemRequest(Long childId, List<String> titles, String note) {}

    /** Gửi cả danh sách (thay toàn bộ danh hiệu của lớp). */
    public record RewardListRequest(List<RewardItemRequest> items) {}

    /** decision: FORWARD / RETURN (PHT) hoặc APPROVE / RETURN (HT). */
    public record RewardDecisionRequest(String decision, String comment) {}
}
