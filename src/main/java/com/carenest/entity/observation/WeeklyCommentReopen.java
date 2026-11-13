package com.carenest.entity.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lần mở lại nhận xét tuần đã gửi (OBS-29): giữ nội dung, người gửi, lúc gửi trước khi mở lại. */
@Getter
@Entity
@Table(name = "weekly_comment_reopens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyCommentReopen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "weekly_comment_id", nullable = false, updatable = false)
    private Long weeklyCommentId;

    @Column(name = "reopened_by", nullable = false, updatable = false)
    private Long reopenedBy;

    @Column(name = "reopened_at", nullable = false, updatable = false)
    private Instant reopenedAt;

    @Column(nullable = false, updatable = false, length = 300)
    private String reason;

    @Column(name = "previous_content", nullable = false, updatable = false, columnDefinition = "text")
    private String previousContent;

    @Column(name = "previous_sent_at", nullable = false, updatable = false)
    private Instant previousSentAt;

    @Column(name = "previous_sent_by", nullable = false, updatable = false)
    private Long previousSentBy;

    public static WeeklyCommentReopen of(WeeklyComment comment, String reason, Long by, Instant at) {
        WeeklyCommentReopen r = new WeeklyCommentReopen();
        r.weeklyCommentId = comment.getId();
        r.reopenedBy = by;
        r.reopenedAt = at;
        r.reason = reason;
        r.previousContent = comment.getContent();
        r.previousSentAt = comment.getSentAt();
        r.previousSentBy = comment.getSentBy();
        return r;
    }
}
