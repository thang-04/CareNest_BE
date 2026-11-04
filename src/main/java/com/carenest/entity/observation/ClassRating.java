package com.carenest.entity.observation;

import com.carenest.utils.enums.ClassRatingLevel;
import com.carenest.utils.enums.ClassRatingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Xếp loại một nhóm lớp trong một kỳ theo 7 tiêu chí của sổ chỉ đạo chuyên môn (mục 7); BGH chấm. */
@Getter
@Entity
@Table(name = "class_ratings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ClassRatingPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ClassRatingLevel records;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ClassRatingLevel program;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ClassRatingLevel theme;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ClassRatingLevel environment;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ClassRatingLevel routine;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ClassRatingLevel health;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ClassRatingLevel parents;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ClassRatingLevel overall;

    @Column(length = 500)
    private String note;

    @Column(name = "rated_by", nullable = false)
    private Long ratedBy;

    @Column(name = "rated_at", nullable = false)
    private Instant ratedAt;

    // HT và PHT cùng sửa một lớp: người lưu sau nhận 409 thay vì ghi đè âm thầm
    @Version
    private long version;

    public static ClassRating create(Long classroomId, Long campusId, Long schoolYearId, ClassRatingPeriod period) {
        ClassRating rating = new ClassRating();
        rating.classroomId = classroomId;
        rating.campusId = campusId;
        rating.schoolYearId = schoolYearId;
        rating.period = period;
        return rating;
    }

    /** levels theo thứ tự: hồ sơ, chương trình, chủ đề, môi trường, nền nếp, sức khỏe, cha mẹ, xếp loại chung. */
    public void rate(ClassRatingLevel[] levels, String note, Long userId, Instant at) {
        this.records = levels[0];
        this.program = levels[1];
        this.theme = levels[2];
        this.environment = levels[3];
        this.routine = levels[4];
        this.health = levels[5];
        this.parents = levels[6];
        this.overall = levels[7];
        this.note = note;
        this.ratedBy = userId;
        this.ratedAt = at;
    }
}
