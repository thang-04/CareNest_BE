package com.carenest.entity.health;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Phiếu theo dõi sức khỏe của trẻ, mở khi kết luận "theo dõi sát" (user chốt 2026-10-31); mỗi trẻ tối đa một phiếu mở.
 * Trạng thái, vòng trao đổi, hạn tính từ dòng thời gian {@link HealthFollowUpEvent} khi đọc.
 */
@Getter
@Entity
@Table(name = "health_follow_ups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthFollowUp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false, updatable = false)
    private Long childId;

    /** Điểm trường của trẻ lúc mở phiếu. */
    @Column(name = "campus_id", nullable = false, updatable = false)
    private Long campusId;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "close_reason", length = 300)
    private String closeReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_outcome", length = 30)
    private HealthFollowUpOutcome closeOutcome;

    @Column(name = "close_condition_text", length = 100)
    private String closeConditionText;

    public static HealthFollowUp open(Long childId, Long campusId, Instant at) {
        HealthFollowUp f = new HealthFollowUp();
        f.childId = childId;
        f.campusId = campusId;
        f.openedAt = at;
        f.updatedAt = at;
        return f;
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    public void touch(Instant at) {
        this.updatedAt = at;
    }

    /** Đóng phiếu; {@code outcome} null khi hệ thống tự đóng (không đổi mức theo dõi của trẻ). */
    public void close(Long userId, String reason, HealthFollowUpOutcome outcome, String conditionText, Instant at) {
        this.closedAt = at;
        this.closedBy = userId;
        this.closeReason = reason;
        this.closeOutcome = outcome;
        this.closeConditionText = conditionText;
        this.updatedAt = at;
    }
}
