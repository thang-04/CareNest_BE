package com.carenest.entity.child;

import com.carenest.utils.enums.PlannedAdmissionStatus;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "planned_admissions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannedAdmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "classroom_id")
    private Long classroomId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlannedAdmissionStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PlannedAdmission create(
            Long childId, Long schoolYearId, Long campusId, Long classroomId, LocalDate startDate, Long createdBy) {
        PlannedAdmission admission = new PlannedAdmission();
        admission.childId = childId;
        admission.schoolYearId = schoolYearId;
        admission.campusId = campusId;
        admission.classroomId = classroomId;
        admission.startDate = startDate;
        admission.status = classroomId == null ? PlannedAdmissionStatus.PENDING : PlannedAdmissionStatus.PLACED;
        admission.createdBy = createdBy;
        return admission;
    }

    public void assignClass(Long classroomId) {
        this.classroomId = classroomId;
        this.status = classroomId == null ? PlannedAdmissionStatus.PENDING : PlannedAdmissionStatus.PLACED;
    }

    public void cancel() {
        this.status = PlannedAdmissionStatus.CANCELLED;
    }

    public void activate() {
        this.status = PlannedAdmissionStatus.ACTIVATED;
    }
}
