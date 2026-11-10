package com.carenest.entity.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "class_daily_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassDailyComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ClassDailyComment create(Long classroomId, Long campusId, LocalDate date) {
        ClassDailyComment comment = new ClassDailyComment();
        comment.classroomId = classroomId;
        comment.campusId = campusId;
        comment.date = date;
        return comment;
    }

    public void record(String text, Long userId, Instant at) {
        this.text = text;
        this.recordedBy = userId;
        this.updatedAt = at;
    }
}
