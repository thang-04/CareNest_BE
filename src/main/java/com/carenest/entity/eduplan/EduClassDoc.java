package com.carenest.entity.eduplan;

import com.carenest.utils.enums.EduClassDocStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Tài liệu kế hoạch của lớp (EDU-12): chủ đề nhánh, kế hoạch chủ đề, giáo án tuần (kế hoạch tuần + 5 kế hoạch ngày).
 * Nội dung soạn, người duyệt và lịch sử lưu JSONB (chuỗi JSON ở tầng Java).
 */
@Getter
@Entity
@Table(name = "edu_class_docs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduClassDoc {
    public static final String BRANCH = "branch";
    public static final String THEME = "theme";
    public static final String WEEK = "week";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_key", nullable = false, length = 80)
    private String docKey;

    @Column(nullable = false, length = 10)
    private String kind;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "year_plan_id", nullable = false)
    private Long yearPlanId;

    @Column(name = "theme_id")
    private Long themeId;

    @Column(name = "week_start")
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EduClassDocStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String approvals;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String history;

    // Spec 004: bản chép từ tài liệu cùng lớp ở phiên bản kế hoạch năm cũ
    @Column(name = "source_doc_id")
    private Long sourceDocId;

    // "Kế hoạch điều chỉnh và bổ sung" theo ngày của giáo án tuần; tách khỏi nội dung để sửa được sau khi duyệt
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String adjustments;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static EduClassDoc create(
            String docKey,
            String kind,
            Long classroomId,
            Long yearPlanId,
            Long themeId,
            LocalDate weekStart,
            Long userId) {
        EduClassDoc doc = new EduClassDoc();
        doc.docKey = docKey;
        doc.kind = kind;
        doc.classroomId = classroomId;
        doc.yearPlanId = yearPlanId;
        doc.themeId = themeId;
        doc.weekStart = weekStart;
        doc.status = EduClassDocStatus.DRAFT;
        doc.content = "{}";
        doc.approvals = "[]";
        doc.history = "[]";
        doc.adjustments = "{}";
        doc.createdBy = userId;
        doc.updatedBy = userId;
        return doc;
    }

    public void changeContent(String content, Long userId) {
        this.content = content;
        this.updatedBy = userId;
    }

    public void changeStatus(EduClassDocStatus status, String approvals, String history, Long userId) {
        this.status = status;
        this.approvals = approvals;
        this.history = history;
        this.updatedBy = userId;
    }

    public void linkSource(Long sourceDocId) {
        this.sourceDocId = sourceDocId;
    }

    public void changeAdjustments(String adjustments, String history, Long userId) {
        this.adjustments = adjustments;
        this.history = history;
        this.updatedBy = userId;
    }
}
