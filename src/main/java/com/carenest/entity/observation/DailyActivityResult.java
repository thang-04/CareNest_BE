package com.carenest.entity.observation;

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

/** Kết quả một dòng hoạt động của giáo án đã duyệt trong ngày: MT đạt / cần cố gắng / chưa đạt (EDU-19). */
@Getter
@Entity
@Table(name = "daily_activity_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyActivityResult {

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

    @Column(name = "activity_key", nullable = false, length = 40)
    private String activityKey;

    @Column(name = "class_doc_id", nullable = false)
    private Long classDocId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Participation participation;

    @Convert(converter = ObservationConverters.Ids.class)
    @Column(name = "goals_met", nullable = false, length = 500)
    private List<Long> goalsMet = List.of();

    @Convert(converter = ObservationConverters.Ids.class)
    @Column(name = "goals_effort", nullable = false, length = 500)
    private List<Long> goalsEffort = List.of();

    @Convert(converter = ObservationConverters.Ids.class)
    @Column(name = "goals_not_met", nullable = false, length = 500)
    private List<Long> goalsNotMet = List.of();

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public static DailyActivityResult start(
            Long childId,
            Long classroomId,
            Long campusId,
            LocalDate date,
            String activityKey,
            Long userId,
            Instant at) {
        DailyActivityResult r = new DailyActivityResult();
        r.childId = childId;
        r.classroomId = classroomId;
        r.campusId = campusId;
        r.date = date;
        r.activityKey = activityKey;
        r.recordedBy = userId;
        r.recordedAt = at;
        return r;
    }

    public void record(
            Long classDocId, Participation participation, List<Long> met, List<Long> effort, List<Long> notMet) {
        this.classDocId = classDocId;
        this.participation = participation;
        this.goalsMet = List.copyOf(met);
        this.goalsEffort = List.copyOf(effort);
        this.goalsNotMet = List.copyOf(notMet);
    }

    public void touch(Long userId, Instant at) {
        this.updatedBy = userId;
        this.updatedAt = at;
    }
}
