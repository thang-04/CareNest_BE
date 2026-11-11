package com.carenest.entity.observation;

import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Getter
@Entity
@Table(name = "semester_assessments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SemesterAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Column(name = "semester_no", nullable = false)
    private int semester;

    @Column(name = "period_start", nullable = false)
    private LocalDate start;

    @Column(name = "period_end", nullable = false)
    private LocalDate end;

    @Column(length = 1000)
    private String note;

    @Column(name = "result_snapshot", columnDefinition = "text")
    private String snapshot;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    private long version;

    public static SemesterAssessment open(
            Long cls, Long campus, Long year, int semester, LocalDate start, LocalDate end) {
        var e = new SemesterAssessment();
        e.classroomId = cls;
        e.campusId = campus;
        e.schoolYearId = year;
        e.semester = semester;
        e.start = start;
        e.end = end;
        return e;
    }

    public void save(String note) {
        this.note = note;
    }

    public void confirm(String json, Long user, Instant at) {
        this.snapshot = json;
        this.confirmedBy = user;
        this.confirmedAt = at;
    }
}
