package com.carenest.entity.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** Một lần đặt giờ chốt; không sửa dòng cũ (lịch sử). */
@Getter
@Entity
@Table(name = "attendance_cutoffs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceCutoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Column(name = "cutoff_time", nullable = false)
    private LocalTime cutoffTime;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AttendanceCutoff create(
            Long schoolYearId, LocalTime cutoffTime, LocalDate effectiveFrom, String note, Long createdBy) {
        AttendanceCutoff cutoff = new AttendanceCutoff();
        cutoff.schoolYearId = schoolYearId;
        cutoff.cutoffTime = cutoffTime;
        cutoff.effectiveFrom = effectiveFrom;
        cutoff.note = note;
        cutoff.createdBy = createdBy;
        return cutoff;
    }
}
