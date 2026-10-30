package com.carenest.entity.attendance;

import com.carenest.utils.enums.HealthAction;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.HealthSymptom;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lần ghi nhận sức khỏe trong ngày của trẻ; chỉ thêm, không sửa. */
@Getter
@Entity
@Table(name = "daily_health_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyHealthEntry {

    /** Chưa có module thông báo: lần ghi nhận chờ gửi phụ huynh. */
    public static final String NOTIFICATION_PENDING = "PENDING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(precision = 3, scale = 1)
    private BigDecimal temperature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HealthObservation observation;

    @Convert(converter = EnumListConverter.Symptoms.class)
    @Column(nullable = false, length = 100)
    private List<HealthSymptom> symptoms;

    @Column(nullable = false, length = 500)
    private String note;

    @Convert(converter = EnumListConverter.Actions.class)
    @Column(nullable = false, length = 100)
    private List<HealthAction> actions;

    @Column(name = "referral_place", length = 150)
    private String referralPlace;

    @Column(name = "referral_time")
    private LocalTime referralTime;

    @Column(name = "referral_escort", length = 100)
    private String referralEscort;

    @Column(name = "notification_status", nullable = false, length = 20)
    private String notificationStatus;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Builder
    private DailyHealthEntry(
            String requestId,
            Long childId,
            Long classroomId,
            Long campusId,
            LocalDate date,
            BigDecimal temperature,
            HealthObservation observation,
            List<HealthSymptom> symptoms,
            String note,
            List<HealthAction> actions,
            String referralPlace,
            LocalTime referralTime,
            String referralEscort,
            Long recordedBy,
            Instant recordedAt) {
        this.requestId = requestId;
        this.childId = childId;
        this.classroomId = classroomId;
        this.campusId = campusId;
        this.date = date;
        this.temperature = temperature;
        this.observation = observation;
        this.symptoms = List.copyOf(symptoms);
        this.note = note;
        this.actions = List.copyOf(actions);
        this.referralPlace = referralPlace;
        this.referralTime = referralTime;
        this.referralEscort = referralEscort;
        this.notificationStatus = NOTIFICATION_PENDING;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }
}
