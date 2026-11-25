package com.carenest.entity.eduplan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Đoạn tham khảo cho RAG soạn giáo án (MT, hoạt động, giáo án mẫu). Cột {@code embedding} (pgvector) không ánh xạ: chỉ
 * ghi/đọc qua native query ở repository. Không chứa dữ liệu trẻ (AI-04).
 */
@Getter
@Entity
@Table(name = "edu_rag_chunks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EduRagChunk {

    @Id
    @Column(length = 80)
    private String id;

    @Column(name = "chunk_type", nullable = false, length = 20)
    private String chunkType;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String meta;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 100)
    private String model;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Đoạn mới từ file seed: chưa có vector (model null) ⇒ indexer embed lười sau. */
    public static EduRagChunk seed(
            String id, String chunkType, String source, String content, String meta, String hash) {
        EduRagChunk chunk = new EduRagChunk();
        chunk.id = id;
        chunk.chunkType = chunkType;
        chunk.source = source;
        chunk.content = content;
        chunk.meta = meta;
        chunk.status = "ACTIVE";
        chunk.contentHash = hash;
        chunk.createdAt = Instant.now();
        chunk.updatedAt = chunk.createdAt;
        return chunk;
    }
}
