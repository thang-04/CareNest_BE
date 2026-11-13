package com.carenest.entity.observation;

import com.carenest.utils.enums.WeeklyCommentSource;
import com.carenest.utils.enums.WeeklyCommentStatus;
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

/** Nhận xét tuần của một trẻ (OBS-29): GV lớp viết, gửi phụ huynh; đã gửi thì khóa. */
@Getter
@Entity
@Table(name = "weekly_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyComment {
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

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false, columnDefinition = "text")
    private String content = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WeeklyCommentStatus status = WeeklyCommentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_source", nullable = false, length = 10)
    private WeeklyCommentSource draftSource = WeeklyCommentSource.MANUAL;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static WeeklyComment create(
            Long childId,
            Long classroomId,
            Long campusId,
            Long schoolYearId,
            LocalDate weekStart,
            Long by,
            Instant at) {
        WeeklyComment c = new WeeklyComment();
        c.childId = childId;
        c.classroomId = classroomId;
        c.campusId = campusId;
        c.schoolYearId = schoolYearId;
        c.weekStart = weekStart;
        c.createdBy = by;
        c.createdAt = at;
        c.updatedAt = at;
        return c;
    }

    public boolean isSent() {
        return status == WeeklyCommentStatus.SENT;
    }

    public void write(String text, WeeklyCommentSource source, Long by, Instant at) {
        this.content = text;
        this.draftSource = source;
        this.updatedBy = by;
        this.updatedAt = at;
    }

    /** Mở lại bản đã gửi: về nháp, giữ nội dung; phụ huynh không thấy tới khi GV gửi lại. */
    public void reopen(Long by, Instant at) {
        this.status = WeeklyCommentStatus.DRAFT;
        this.sentBy = null;
        this.sentAt = null;
        this.updatedBy = by;
        this.updatedAt = at;
    }

    public void send(Long by, Instant at) {
        this.status = WeeklyCommentStatus.SENT;
        this.sentBy = by;
        this.sentAt = at;
        this.updatedBy = by;
        this.updatedAt = at;
    }
}
