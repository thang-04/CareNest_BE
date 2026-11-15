package com.carenest.entity.observation;

import com.carenest.utils.enums.EvaluationLockKind;
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
 * Mở lại đánh giá cuối chủ đề / học kỳ / cuối năm đã khóa của một lớp – kỳ tới hạn validUntil (HT, PHT điểm trường).
 * periodKey: dòng gốc chủ đề (THEME), 1 | 2 (SEMESTER), Y (YEAR).
 */
@Getter
@Entity
@Table(name = "evaluation_unlocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvaluationLockKind kind;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "period_key", nullable = false, length = 40)
    private String periodKey;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(name = "unlocked_by", nullable = false)
    private Long unlockedBy;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static EvaluationUnlock create(
            EvaluationLockKind kind,
            Long classroomId,
            Long campusId,
            String periodKey,
            Instant validUntil,
            String reason,
            Long unlockedBy,
            Instant unlockedAt) {
        EvaluationUnlock unlock = new EvaluationUnlock();
        unlock.kind = kind;
        unlock.classroomId = classroomId;
        unlock.campusId = campusId;
        unlock.periodKey = periodKey;
        unlock.validUntil = validUntil;
        unlock.reason = reason;
        unlock.unlockedBy = unlockedBy;
        unlock.unlockedAt = unlockedAt;
        return unlock;
    }

    public boolean isActiveAt(Instant now) {
        return validUntil.isAfter(now);
    }

    /** Kết thúc sớm: hạn = lúc thu hồi, giữ bản ghi làm lịch sử. */
    public void revoke(Long userId, Instant at) {
        this.validUntil = at;
        this.revokedBy = userId;
        this.revokedAt = at;
    }
}
