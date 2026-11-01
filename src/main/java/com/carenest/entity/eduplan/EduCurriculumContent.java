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

/** Gợi ý nội dung từ mục C "Nội dung giáo dục theo độ tuổi" của Chương trình GDMN (EDU-28). */
@Getter
@Entity
@Table(name = "edu_curriculum_contents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduCurriculumContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "age_group_id", nullable = false)
    private Long ageGroupId;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Column(name = "sub_domain_id")
    private Long subDomainId;

    @Column(name = "category_no", length = 10)
    private String categoryNo;

    @Column(nullable = false, columnDefinition = "text")
    private String category;

    @Column(length = 300)
    private String label;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "section_ref", nullable = false, length = 150)
    private String sectionRef;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
