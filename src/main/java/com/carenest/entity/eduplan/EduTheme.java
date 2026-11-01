package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Chủ đề của kế hoạch năm; lớp dùng chủ đề và đợt nghỉ để chia tuần nên khóa khi kế hoạch đã ban hành. */
@Getter
@Entity
@Table(name = "edu_themes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduTheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_plan_id", nullable = false, updatable = false)
    private Long yearPlanId;

    // Định danh ổn định qua các phiên bản (NULL = dòng gốc, lineage là chính id), spec 004
    @Column(name = "lineage_id", updatable = false)
    private Long lineageId;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String abbr;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false, columnDefinition = "text")
    private String festivals;

    @Column(nullable = false, columnDefinition = "text")
    private String note;

    public static EduTheme create(Long yearPlanId) {
        EduTheme theme = new EduTheme();
        theme.yearPlanId = yearPlanId;
        return theme;
    }

    public void update(
            short sortOrder,
            String name,
            String abbr,
            LocalDate startDate,
            LocalDate endDate,
            String festivals,
            String note) {
        this.sortOrder = sortOrder;
        this.name = name;
        this.abbr = abbr;
        this.startDate = startDate;
        this.endDate = endDate;
        this.festivals = festivals;
        this.note = note;
    }

    public Long lineage() {
        return lineageId == null ? id : lineageId;
    }

    public static EduTheme copyFor(Long yearPlanId, EduTheme source) {
        EduTheme theme = create(yearPlanId);
        theme.lineageId = source.lineage();
        theme.update(
                source.sortOrder,
                source.name,
                source.abbr,
                source.startDate,
                source.endDate,
                source.festivals,
                source.note);
        return theme;
    }
}
