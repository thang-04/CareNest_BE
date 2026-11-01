package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Nội dung giáo dục của 1 MT. */
@Getter
@Entity
@Table(name = "edu_goal_contents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduGoalContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalRowId;

    // Định danh ổn định qua các phiên bản (NULL = dòng gốc, lineage là chính id), spec 004
    @Column(name = "lineage_id", updatable = false)
    private Long lineageId;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    public static EduGoalContent create() {
        return new EduGoalContent();
    }

    public void place(Long goalRowId, short sortOrder, String text) {
        this.goalRowId = goalRowId;
        this.sortOrder = sortOrder;
        this.text = text;
    }

    public Long lineage() {
        return lineageId == null ? id : lineageId;
    }

    public static EduGoalContent copyFor(Long goalRowId, EduGoalContent source) {
        EduGoalContent content = create();
        content.lineageId = source.lineage();
        content.place(goalRowId, source.sortOrder, source.text);
        return content;
    }
}
