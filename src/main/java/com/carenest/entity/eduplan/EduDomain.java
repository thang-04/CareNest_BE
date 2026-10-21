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

/** Lĩnh vực giáo dục; name là khóa nghiệp vụ theo Chương trình, displayLabel do trường đặt. */
@Getter
@Entity
@Table(name = "edu_domains")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "display_label", length = 150)
    private String displayLabel;

    @Column(name = "short_label", length = 60)
    private String shortLabel;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
