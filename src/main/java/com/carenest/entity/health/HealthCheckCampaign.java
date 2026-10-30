package com.carenest.entity.health;

import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Đợt khám sức khỏe hoặc kỳ cân đo của một điểm trường (HLT-01, HLT-08). Loại và điểm trường không đổi sau khi tạo. */
@Getter
@Entity
@Table(name = "health_check_campaigns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthCheckCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HealthCheckKind kind;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 150)
    private String examiner;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Builder
    private HealthCheckCampaign(
            HealthCheckKind kind,
            Long campusId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String examiner,
            String note,
            Long createdBy,
            Instant createdAt) {
        this.kind = kind;
        this.campusId = campusId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.examiner = examinerOf(kind, examiner);
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public HealthCheckStatus statusOn(LocalDate today) {
        if (cancelledAt != null) {
            return HealthCheckStatus.CANCELLED;
        }
        if (closedAt != null) {
            return HealthCheckStatus.CLOSED;
        }
        return today.isBefore(startDate) ? HealthCheckStatus.UPCOMING : HealthCheckStatus.IN_PROGRESS;
    }

    /** Đã đóng hoặc đã hủy: không sửa thông tin, không nhập kết quả. */
    public boolean isEnded() {
        return closedAt != null || cancelledAt != null;
    }

    public void update(String name, LocalDate startDate, LocalDate endDate, String examiner, String note, Instant at) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.examiner = examinerOf(kind, examiner);
        this.note = note;
        this.updatedAt = at;
    }

    public void close(Long userId, Instant at) {
        this.closedAt = at;
        this.closedBy = userId;
        this.updatedAt = at;
    }

    public void reopen(Instant at) {
        this.closedAt = null;
        this.closedBy = null;
        this.updatedAt = at;
    }

    public void cancel(Instant at) {
        this.cancelledAt = at;
        this.updatedAt = at;
    }

    // Kỳ cân đo do giáo viên cân đo, không có đơn vị khám
    private static String examinerOf(HealthCheckKind kind, String examiner) {
        return kind == HealthCheckKind.MEASUREMENT ? "" : examiner;
    }
}
