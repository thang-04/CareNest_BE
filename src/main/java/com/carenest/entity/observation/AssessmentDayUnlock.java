package com.carenest.entity.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Mở lại ngày đánh giá một lớp đã khóa tới hạn validUntil (HT, PHT điểm trường). */
@Getter
@Entity
@Table(name = "assessment_day_unlocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentDayUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(nullable = false)
    private LocalDate date;

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

    public static AssessmentDayUnlock create(
            Long classroomId,
            Long campusId,
            LocalDate date,
            Instant validUntil,
            String reason,
            Long unlockedBy,
            Instant unlockedAt) {
        AssessmentDayUnlock unlock = new AssessmentDayUnlock();
        unlock.classroomId = classroomId;
        unlock.campusId = campusId;
        unlock.date = date;
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
