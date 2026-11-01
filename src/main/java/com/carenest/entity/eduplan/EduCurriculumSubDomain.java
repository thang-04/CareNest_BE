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

/** Lĩnh vực con theo nhóm tuổi trong Chương trình GDMN (EDU-28), tầng trên của nhóm kết quả mong đợi. */
@Getter
@Entity
@Table(name = "edu_curriculum_sub_domains")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduCurriculumSubDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "age_group_id", nullable = false)
    private Long ageGroupId;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Column(nullable = false, length = 150)
    private String text;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
