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

/** Văn bản của Bộ dùng làm nguồn danh mục chương trình (EDU-28); chỉ đọc, nạp bằng migration. */
@Getter
@Entity
@Table(name = "edu_curriculum_sources")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduCurriculumSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "document_no", nullable = false, length = 50)
    private String documentNo;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(nullable = false, length = 150)
    private String issuer;

    @Column(name = "official_url", nullable = false, length = 500)
    private String officialUrl;

    @Column(name = "retrieved_from_url", nullable = false, length = 500)
    private String retrievedFromUrl;

    @Column(name = "retrieved_at", nullable = false)
    private LocalDate retrievedAt;

    @Column(name = "scope_note", nullable = false, columnDefinition = "text")
    private String scopeNote;

    @Column(nullable = false)
    private boolean active;
}
