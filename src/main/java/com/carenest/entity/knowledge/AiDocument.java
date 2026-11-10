package com.carenest.entity.knowledge;

import com.carenest.utils.enums.AiDocumentStatus;
import com.carenest.utils.enums.AiDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Tài liệu tham khảo đã duyệt cho RAG; nội dung nằm ở các chunk. */
@Getter
@Entity
@Table(name = "ai_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiDocument {

    public static final String REGION_ALL = "ALL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private AiDocumentType documentType;

    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(length = 50)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiDocumentStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AiDocument create(
            String title,
            AiDocumentType documentType,
            String sourceName,
            String sourceUrl,
            String region,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String version,
            Long createdBy) {
        AiDocument document = new AiDocument();
        document.title = title;
        document.documentType = documentType;
        document.sourceName = sourceName;
        document.sourceUrl = sourceUrl;
        document.region = region;
        document.effectiveDate = effectiveDate;
        document.expiryDate = expiryDate;
        document.version = version;
        document.status = AiDocumentStatus.ACTIVE;
        document.createdBy = createdBy;
        return document;
    }

    public void changeStatus(AiDocumentStatus status) {
        this.status = status;
    }
}
