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

/** Mục tiêu theo nhóm tuổi từ mục D "Kết quả mong đợi" của Chương trình GDMN (EDU-28). */
@Getter
@Entity
@Table(name = "edu_curriculum_goals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduCurriculumGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "age_group_id", nullable = false)
    private Long ageGroupId;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Column(name = "expected_result_id", nullable = false)
    private Long expectedResultId;

    @Column(name = "goal_no", length = 10)
    private String goalNo;

    @Column(name = "goal_text", nullable = false, columnDefinition = "text")
    private String goalText;

    @Column(name = "section_ref", nullable = false, length = 150)
    private String sectionRef;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
