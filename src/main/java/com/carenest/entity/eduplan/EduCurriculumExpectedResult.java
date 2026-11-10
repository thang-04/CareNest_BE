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

/** Nhóm kết quả mong đợi (tiêu đề hàng "n. …" mục D) của Chương trình GDMN (EDU-28); lĩnh vực con tùy chọn. */
@Getter
@Entity
@Table(name = "edu_curriculum_expected_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduCurriculumExpectedResult {

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

    @Column(name = "group_no", length = 10)
    private String groupNo;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
