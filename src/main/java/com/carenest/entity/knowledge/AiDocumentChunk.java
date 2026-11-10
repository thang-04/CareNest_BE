package com.carenest.entity.knowledge;

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

/**
 * Một đoạn tài liệu. Cột embedding (pgvector) không map vào JPA: chỉ đọc/ghi qua native query trong
 * AiDocumentChunkRepository, nên không cần thêm thư viện kiểu vector cho Hibernate.
 */
@Getter
@Entity
@Table(name = "ai_document_chunks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiDocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AiDocumentChunk create(Long documentId, int chunkIndex, String content, String contentHash) {
        AiDocumentChunk chunk = new AiDocumentChunk();
        chunk.documentId = documentId;
        chunk.chunkIndex = chunkIndex;
        chunk.content = content;
        chunk.contentHash = contentHash;
        return chunk;
    }
}
