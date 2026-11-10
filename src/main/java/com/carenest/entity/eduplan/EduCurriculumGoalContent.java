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

/** Gạch đầu dòng chỉ báo dưới một mục tiêu của Bộ; lấy vào kế hoạch thành nội dung giáo dục. */
@Getter
@Entity
@Table(name = "edu_curriculum_goal_contents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduCurriculumGoalContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "curriculum_goal_id", nullable = false)
    private Long curriculumGoalId;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;
}
