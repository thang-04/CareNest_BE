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

    public static final LocalTime DEFAULT_LATEST_ARRIVAL = LocalTime.of(14, 0);
    public static final LocalTime DEFAULT_DROPOFF_OPEN_FROM = LocalTime.of(6, 30);
    public static final LocalTime DEFAULT_MORNING_START = LocalTime.of(7, 0);
    public static final LocalTime DEFAULT_MORNING_END = LocalTime.of(11, 0);
    public static final LocalTime DEFAULT_AFTERNOON_START = LocalTime.of(13, 30);
    public static final LocalTime DEFAULT_PICKUP_START = LocalTime.of(15, 0);
    public static final LocalTime DEFAULT_FULL_DAY_PICKUP_FROM = LocalTime.of(15, 0);
    public static final LocalTime DEFAULT_PICKUP_END = LocalTime.of(17, 0);
    public static final LocalTime DEFAULT_AFTERNOON_END = LocalTime.of(17, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Column(name = "cutoff_time", nullable = false)
    private LocalTime cutoffTime;

    /** ATT-13: trẻ nghỉ sáng chưa đến tới giờ này thì tự thành Vắng cả ngày. */
    @Column(name = "latest_afternoon_arrival", nullable = false)
    private LocalTime latestAfternoonArrival = DEFAULT_LATEST_ARRIVAL;

    @Column(name = "dropoff_open_from", nullable = false)
    private LocalTime dropoffOpenFrom = DEFAULT_DROPOFF_OPEN_FROM;

    @Column(name = "morning_start", nullable = false)
    private LocalTime morningStart = DEFAULT_MORNING_START;

    @Column(name = "morning_end", nullable = false)
    private LocalTime morningEnd = DEFAULT_MORNING_END;

    @Column(name = "afternoon_start", nullable = false)
    private LocalTime afternoonStart = DEFAULT_AFTERNOON_START;

    @Column(name = "pickup_start", nullable = false)
    private LocalTime pickupStart = DEFAULT_PICKUP_START;

    @Column(name = "full_day_pickup_from", nullable = false)
    private LocalTime fullDayPickupFrom = DEFAULT_FULL_DAY_PICKUP_FROM;

    @Column(name = "pickup_end", nullable = false)
    private LocalTime pickupEnd = DEFAULT_PICKUP_END;

    @Column(name = "afternoon_end", nullable = false)
    private LocalTime afternoonEnd = DEFAULT_AFTERNOON_END;

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

    public AttendanceCutoff withLatestArrival(LocalTime latestArrival) {
        this.latestAfternoonArrival = latestArrival == null ? DEFAULT_LATEST_ARRIVAL : latestArrival;
        return this;
    }

    public AttendanceCutoff withDailySchedule(
            LocalTime dropoffOpenFrom,
            LocalTime morningStart,
            LocalTime morningEnd,
            LocalTime afternoonStart,
            LocalTime pickupStart,
            LocalTime fullDayPickupFrom,
            LocalTime pickupEnd,
            LocalTime afternoonEnd) {
        this.dropoffOpenFrom = dropoffOpenFrom == null ? DEFAULT_DROPOFF_OPEN_FROM : dropoffOpenFrom;
        this.morningStart = morningStart == null ? DEFAULT_MORNING_START : morningStart;
        this.morningEnd = morningEnd == null ? DEFAULT_MORNING_END : morningEnd;
        this.afternoonStart = afternoonStart == null ? DEFAULT_AFTERNOON_START : afternoonStart;
        this.pickupStart = pickupStart == null ? DEFAULT_PICKUP_START : pickupStart;
        this.fullDayPickupFrom = fullDayPickupFrom == null ? DEFAULT_FULL_DAY_PICKUP_FROM : fullDayPickupFrom;
        this.pickupEnd = pickupEnd == null ? DEFAULT_PICKUP_END : pickupEnd;
        this.afternoonEnd = afternoonEnd == null ? DEFAULT_AFTERNOON_END : afternoonEnd;
        return this;
    }
}
