package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "edu_expected_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduExpectedResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "age_group_id", nullable = false)
    private Long ageGroupId;

    @Column(name = "domain_id", nullable = false)
    private Long domainId;

    @Column(name = "sub_domain", length = 150)
    private String subDomain;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
