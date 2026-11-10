package com.carenest.entity.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Nhận xét chung cả lớp một tháng (OBS-26): nháp hệ thống viết, GV lớp sửa rồi xác nhận; xác nhận xong không sửa. */
@Getter
@Entity
@Table(name = "class_monthly_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassMonthlyComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Column(nullable = false)
    private LocalDate month;

    @Column(name = "draft_content", columnDefinition = "text")
    private String draftContent;

    @Column(length = 3000)
    private String content;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static ClassMonthlyComment draft(
            Long classroomId, Long campusId, Long schoolYearId, LocalDate month, String draft, Instant at) {
        ClassMonthlyComment c = new ClassMonthlyComment();
        c.classroomId = classroomId;
        c.campusId = campusId;
        c.schoolYearId = schoolYearId;
        c.month = month;
        c.draftContent = draft;
        c.updatedAt = at;
        return c;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public void save(String text, Long userId, Instant at) {
        this.content = text;
        this.updatedBy = userId;
        this.updatedAt = at;
    }

    public void confirm(String text, Long userId, Instant at) {
        save(text, userId, at);
        this.confirmedBy = userId;
        this.confirmedAt = at;
    }
}
