package com.carenest.entity.observation;

import com.carenest.utils.enums.SummaryPeriodType;
import com.carenest.utils.enums.SummarySource;
import com.carenest.utils.enums.SummaryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Nhận xét tổng hợp của trẻ (ADR-0008). aiDraft là bản AI soạn, bất biến sau khi tạo; content là bản GV sửa và
 * thành bản chính thức khi APPROVED. AI không bao giờ tự duyệt.
 */
@Getter
@Entity
@Table(name = "child_summaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private SummaryPeriodType periodType;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SummarySource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SummaryStatus status;

    @Column(name = "ai_draft", updatable = false, columnDefinition = "text")
    private String aiDraft;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "ai_provider", updatable = false, length = 50)
    private String aiProvider;

    @Column(name = "ai_model", updatable = false, length = 100)
    private String aiModel;

    @Column(name = "ai_prompt_version", updatable = false, length = 50)
    private String aiPromptVersion;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChildSummary fromAi(
            Long childId,
            SummaryPeriodType periodType,
            LocalDate periodFrom,
            LocalDate periodTo,
            String aiDraft,
            String content,
            String aiProvider,
            String aiModel,
            String aiPromptVersion,
            Instant generatedAt,
            Long createdBy) {
        ChildSummary summary = draft(childId, periodType, periodFrom, periodTo, content, generatedAt, createdBy);
        summary.source = SummarySource.AI;
        summary.aiDraft = aiDraft;
        summary.aiProvider = aiProvider;
        summary.aiModel = aiModel;
        summary.aiPromptVersion = aiPromptVersion;
        return summary;
    }

    public static ChildSummary fromTemplate(
            Long childId,
            SummaryPeriodType periodType,
            LocalDate periodFrom,
            LocalDate periodTo,
            String content,
            Instant generatedAt,
            Long createdBy) {
        ChildSummary summary = draft(childId, periodType, periodFrom, periodTo, content, generatedAt, createdBy);
        summary.source = SummarySource.TEMPLATE;
        return summary;
    }

    public boolean isApproved() {
        return status == SummaryStatus.APPROVED;
    }

    /** Chỉ sửa được khi còn DRAFT; caller kiểm trạng thái và quyền trước. */
    public void editContent(String content) {
        this.content = content;
    }

    public void approve(Long approvedBy, Instant approvedAt) {
        this.status = SummaryStatus.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    private static ChildSummary draft(
            Long childId,
            SummaryPeriodType periodType,
            LocalDate periodFrom,
            LocalDate periodTo,
            String content,
            Instant generatedAt,
            Long createdBy) {
        ChildSummary summary = new ChildSummary();
        summary.childId = childId;
        summary.periodType = periodType;
        summary.periodFrom = periodFrom;
        summary.periodTo = periodTo;
        summary.status = SummaryStatus.DRAFT;
        summary.content = content;
        summary.generatedAt = generatedAt;
        summary.createdBy = createdBy;
        return summary;
    }
}
