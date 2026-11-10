package com.carenest.entity.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Đánh giá cuối chủ đề của một lớp mẫu giáo (OBS-27): ô GV sửa tay và ghi chú; xác nhận thì lưu cả bảng, không sửa nữa.
 * cells là JSON do service đọc/ghi.
 */
@Getter
@Entity
@Table(name = "theme_evaluations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThemeEvaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Column(name = "theme_id", nullable = false)
    private Long themeId;

    @Column(name = "theme_name", nullable = false)
    private String themeName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, columnDefinition = "text")
    private String cells;

    @Column(length = 1000)
    private String note;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static ThemeEvaluation open(
            Long classroomId,
            Long campusId,
            Long schoolYearId,
            Long themeId,
            String themeName,
            LocalDate startDate,
            LocalDate endDate,
            Instant at) {
        ThemeEvaluation e = new ThemeEvaluation();
        e.classroomId = classroomId;
        e.campusId = campusId;
        e.schoolYearId = schoolYearId;
        e.themeId = themeId;
        e.themeName = themeName;
        e.startDate = startDate;
        e.endDate = endDate;
        e.cells = "[]";
        e.updatedAt = at;
        return e;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public void save(String cellsJson, String note, Long userId, Instant at) {
        this.cells = cellsJson;
        this.note = note;
        this.updatedBy = userId;
        this.updatedAt = at;
    }

    public void confirm(String cellsJson, String note, Long userId, Instant at) {
        save(cellsJson, note, userId, at);
        this.confirmedBy = userId;
        this.confirmedAt = at;
    }
}
