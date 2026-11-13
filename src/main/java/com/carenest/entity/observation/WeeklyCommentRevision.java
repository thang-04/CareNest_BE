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

/** GV sửa nhận xét tuần đã gửi (OBS-29): giữ nội dung, lúc gửi, người gửi trước khi sửa. */
@Getter
@Entity
@Table(name = "weekly_comment_revisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyCommentRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "weekly_comment_id", nullable = false)
    private Long weeklyCommentId;

    @Column(name = "previous_content", nullable = false)
    private String previousContent;

    @Column(name = "previous_sent_at", nullable = false)
    private Instant previousSentAt;

    @Column(name = "previous_sent_by", nullable = false)
    private Long previousSentBy;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public static WeeklyCommentRevision of(WeeklyComment sent, Long by, Instant at) {
        WeeklyCommentRevision r = new WeeklyCommentRevision();
        r.weeklyCommentId = sent.getId();
        r.previousContent = sent.getContent();
        r.previousSentAt = sent.getSentAt();
        r.previousSentBy = sent.getSentBy();
        r.changedBy = by;
        r.changedAt = at;
        return r;
    }
}
