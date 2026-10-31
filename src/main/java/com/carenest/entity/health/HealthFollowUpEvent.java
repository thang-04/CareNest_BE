package com.carenest.entity.health;

import com.carenest.utils.enums.HealthFollowUpAction;
import com.carenest.utils.enums.HealthFollowUpOutcome;
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

/**
 * Một sự kiện trên dòng thời gian phiếu theo dõi; thứ tự id là thứ tự thời gian. {@code eventDate}: ngày khám (OPENED,
 * MEASUREMENT_ADDED), ngày trao đổi (PARENT_CONTACTED), ngày khám chuyên khoa (EXTERNAL_RESULT). Sửa phiếu khám đã kết
 * luận chỉ cập nhật ngày khám, cờ bất thường mới, lời trao đổi tại buổi khám của sự kiện thuộc lần khám đó.
 */
@Getter
@Entity
@Table(name = "health_follow_up_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthFollowUpEvent {

    /** Tự đóng khi trẻ ra trường (chuyển năm học). */
    public static final String AUTO_CHILD_LEFT_SCHOOL = "CHILD_LEFT_SCHOOL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follow_up_id", nullable = false, updatable = false)
    private Long followUpId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private HealthFollowUpAction action;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "measurement_id", updatable = false)
    private Long measurementId;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "new_finding")
    private Boolean newFinding;

    @Column(name = "at_exam", nullable = false, updatable = false)
    private boolean atExam;

    @Column(length = 500)
    private String note;

    @Column(length = 150, updatable = false)
    private String facility;

    @Column(name = "next_visit_on", updatable = false)
    private LocalDate nextVisitOn;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private HealthFollowUpOutcome outcome;

    @Column(name = "condition_text", length = 100, updatable = false)
    private String conditionText;

    @Column(name = "auto_reason", length = 30, updatable = false)
    private String autoReason;

    /** OPENED hoặc MEASUREMENT_ADDED: lần khám vào phiếu; {@code newFinding} chỉ có khi khám theo mẫu FOLLOW_UP. */
    public static HealthFollowUpEvent exam(
            Long followUpId,
            HealthFollowUpAction action,
            Long measurementId,
            LocalDate examOn,
            Boolean newFinding,
            Long userId,
            Instant at) {
        HealthFollowUpEvent e = base(followUpId, action, userId, at);
        e.measurementId = measurementId;
        e.eventDate = examOn;
        e.newFinding = newFinding;
        return e;
    }

    /** Trao đổi với phụ huynh; {@code measurementId} khác null khi bác sĩ trao đổi tại buổi khám đó. */
    public static HealthFollowUpEvent parentContact(
            Long followUpId, LocalDate contactedOn, String note, Long measurementId, Long userId, Instant at) {
        HealthFollowUpEvent e = base(followUpId, HealthFollowUpAction.PARENT_CONTACTED, userId, at);
        e.eventDate = contactedOn;
        e.note = note;
        e.measurementId = measurementId;
        e.atExam = measurementId != null;
        return e;
    }

    public static HealthFollowUpEvent externalResult(
            Long followUpId,
            LocalDate resultOn,
            String note,
            String facility,
            LocalDate nextVisitOn,
            Long userId,
            Instant at) {
        HealthFollowUpEvent e = base(followUpId, HealthFollowUpAction.EXTERNAL_RESULT, userId, at);
        e.eventDate = resultOn;
        e.note = note;
        e.facility = facility;
        e.nextVisitOn = nextVisitOn;
        return e;
    }

    public static HealthFollowUpEvent closed(
            Long followUpId,
            String reason,
            HealthFollowUpOutcome outcome,
            String conditionText,
            String autoReason,
            Long userId,
            Instant at) {
        HealthFollowUpEvent e = base(followUpId, HealthFollowUpAction.CLOSED, userId, at);
        e.note = reason;
        e.outcome = outcome;
        e.conditionText = conditionText;
        e.autoReason = autoReason;
        return e;
    }

    private static HealthFollowUpEvent base(Long followUpId, HealthFollowUpAction action, Long userId, Instant at) {
        HealthFollowUpEvent e = new HealthFollowUpEvent();
        e.followUpId = followUpId;
        e.action = action;
        e.userId = userId;
        e.createdAt = at;
        return e;
    }

    /** Sự kiện lần khám vào phiếu (OPENED, MEASUREMENT_ADDED). */
    public boolean isExamEntry() {
        return action == HealthFollowUpAction.OPENED || action == HealthFollowUpAction.MEASUREMENT_ADDED;
    }

    /** Sửa phiếu khám đã kết luận: ngày khám mới. */
    public void changeExamDate(LocalDate examOn) {
        this.eventDate = examOn;
    }

    public void changeNewFinding(boolean newFinding) {
        this.newFinding = newFinding;
    }

    /** Sửa phiếu khám đã kết luận: lời trao đổi tại buổi khám theo nội dung mới. */
    public void rewriteExamContact(LocalDate examOn, String note) {
        this.eventDate = examOn;
        this.note = note;
    }
}
