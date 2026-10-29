package com.carenest.entity.observation;

import com.carenest.entity.attendance.EnumListConverter;
import com.carenest.utils.enums.Emotion;
import com.carenest.utils.enums.HealthSymptom;
import com.carenest.utils.enums.NapLevel;
import com.carenest.utils.enums.ObservedBehavior;
import com.carenest.utils.enums.Participation;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Nhóm ngày của đánh giá hằng ngày: 1 dòng / trẻ / ngày, gồm cờ và nhận xét dùng chung cho hai view (EDU-19). */
@Getter
@Entity
@Table(name = "daily_observations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "health_overridden", nullable = false)
    private boolean healthOverridden;

    @Convert(converter = EnumListConverter.Symptoms.class)
    @Column(name = "health_signs", nullable = false, length = 100)
    private List<HealthSymptom> healthSigns = List.of();

    @Column(name = "health_note", nullable = false, length = 500)
    private String healthNote = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String meals = "{}";

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NapLevel nap;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Emotion emotion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Participation participation;

    @Convert(converter = ObservationConverters.Behaviors.class)
    @Column(nullable = false, length = 200)
    private List<ObservedBehavior> behaviors = List.of();

    @Column(name = "behavior_note", nullable = false, length = 500)
    private String behaviorNote = "";

    private Boolean flag;

    @Column(nullable = false, length = 500)
    private String comment = "";

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public static DailyObservation start(
            Long childId, Long classroomId, Long campusId, LocalDate date, Long userId, Instant at) {
        DailyObservation o = new DailyObservation();
        o.childId = childId;
        o.classroomId = classroomId;
        o.campusId = campusId;
        o.date = date;
        o.recordedBy = userId;
        o.recordedAt = at;
        return o;
    }

    /** View Hoạt động chung: sức khỏe cô chỉnh (không chỉnh thì theo sức khỏe trong ngày), ăn, ngủ, cảm xúc, tham gia cả ngày, hành vi, cờ. */
    public void recordDay(
            boolean healthOverridden,
            List<HealthSymptom> healthSigns,
            String healthNote,
            String meals,
            NapLevel nap,
            Emotion emotion,
            Participation participation,
            List<ObservedBehavior> behaviors,
            String behaviorNote,
            Boolean flag) {
        this.healthOverridden = healthOverridden;
        this.healthSigns = healthOverridden ? List.copyOf(healthSigns) : List.of();
        this.healthNote = healthNote;
        this.meals = meals;
        this.nap = nap;
        this.emotion = emotion;
        this.participation = participation;
        this.behaviors = List.copyOf(behaviors);
        this.behaviorNote = behaviorNote;
        this.flag = flag;
    }

    /** Nhận xét 1 bản / trẻ / ngày: cả hai view cùng ghi vào đây. */
    public void changeComment(String comment) {
        this.comment = comment;
    }

    public void touch(Long userId, Instant at) {
        this.updatedBy = userId;
        this.updatedAt = at;
    }
}
