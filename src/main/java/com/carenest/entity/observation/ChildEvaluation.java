package com.carenest.entity.observation;

import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.EvaluationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Đánh giá tháng / cuối năm của một trẻ: bản nháp hệ thống tạo, nội dung GV sửa và xác nhận. */
@Getter
@Entity
@Table(name = "child_evaluations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private EvaluationPeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvaluationStatus status;

    @Column(name = "draft_content")
    private String draftContent;

    @Column(name = "draft_at")
    private Instant draftAt;

    @Column(name = "final_content", length = 3000)
    private String finalContent;

    @Column(name = "program_completed")
    private Boolean programCompleted;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @SuppressWarnings("java:S107")
    public static ChildEvaluation draft(
            Long childId,
            Long classroomId,
            Long campusId,
            Long schoolYearId,
            EvaluationPeriodType periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            String content,
            Instant at) {
        ChildEvaluation e = new ChildEvaluation();
        e.childId = childId;
        e.classroomId = classroomId;
        e.campusId = campusId;
        e.schoolYearId = schoolYearId;
        e.periodType = periodType;
        e.periodStart = periodStart;
        e.periodEnd = periodEnd;
        e.status = EvaluationStatus.AWAITING_REVIEW;
        e.draftContent = content;
        e.draftAt = at;
        return e;
    }

    public boolean isConfirmed() {
        return status == EvaluationStatus.CONFIRMED;
    }

    /** Tạo lại bản nháp; nội dung GV đã sửa giữ nguyên. */
    public void redraft(String content, Instant at) {
        this.draftContent = content;
        this.draftAt = at;
    }

    /** Trẻ lớp 5–6 tuổi: hoàn thành / chưa hoàn thành chương trình GDMN (đánh giá cuối năm). */
    public void markProgram(Boolean completed) {
        this.programCompleted = completed;
    }

    public void edit(String content) {
        this.finalContent = content;
    }

    public void confirm(String content, Long userId, Instant at) {
        this.finalContent = content;
        this.status = EvaluationStatus.CONFIRMED;
        this.confirmedBy = userId;
        this.confirmedAt = at;
    }
}
