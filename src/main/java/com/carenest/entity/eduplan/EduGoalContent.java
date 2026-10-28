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

    @Column(name = "goal_row_id", nullable = false)
    private Long goalRowId;

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
}
