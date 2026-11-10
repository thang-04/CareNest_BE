package com.carenest.entity.observation;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Quan sát một trẻ trong một ngày; nhập lại cùng ngày thì ghi đè (upsert). */
@Getter
@Entity
@Table(name = "observations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Observation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "observed_on", nullable = false)
    private LocalDate observedOn;

    @Column(length = 1000)
    private String note;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @ElementCollection
    @CollectionTable(name = "observation_values", joinColumns = @JoinColumn(name = "observation_id"))
    private List<ObservationValue> values = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Observation create(Long childId, Long classroomId, LocalDate observedOn) {
        Observation observation = new Observation();
        observation.childId = childId;
        observation.classroomId = classroomId;
        observation.observedOn = observedOn;
        return observation;
    }

    public void record(String note, Collection<ObservationValue> values, Long recordedBy) {
        this.note = note;
        this.recordedBy = recordedBy;
        this.values.clear();
        this.values.addAll(values);
    }
}
