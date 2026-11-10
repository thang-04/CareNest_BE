package com.carenest.repository.knowledge;

import com.carenest.entity.knowledge.AiDocumentChunk;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Embedding (pgvector) chỉ đọc/ghi qua native query; vector truyền dạng chuỗi "[0.1,0.2,...]". */
public interface AiDocumentChunkRepository extends JpaRepository<AiDocumentChunk, Long> {

    List<AiDocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);

    long countByDocumentId(Long documentId);

    @Modifying
    @Query(value = """
            UPDATE ai_document_chunks
            SET embedding = CAST(:embedding AS vector), embedding_model = :model
            WHERE id = :chunkId
            """, nativeQuery = true)
    int updateEmbedding(
            @Param("chunkId") Long chunkId, @Param("embedding") String embedding, @Param("model") String model);

    // Chunk trùng nội dung đã có embedding cùng model thì chép lại, không gọi API embedding
    @Modifying
    @Query(value = """
            UPDATE ai_document_chunks target
            SET embedding = source.embedding, embedding_model = source.embedding_model
            FROM (SELECT embedding, embedding_model FROM ai_document_chunks
                  WHERE content_hash = :hash AND embedding_model = :model AND embedding IS NOT NULL
                    AND id <> :chunkId
                  LIMIT 1) source
            WHERE target.id = :chunkId
            """, nativeQuery = true)
    int copyEmbeddingFromSameContent(
            @Param("chunkId") Long chunkId, @Param("hash") String hash, @Param("model") String model);

    @Modifying
    @Query(value = """
            UPDATE ai_document_chunks SET embedding = NULL, embedding_model = NULL WHERE document_id = :documentId
            """, nativeQuery = true)
    int clearEmbeddings(@Param("documentId") Long documentId);

    @Modifying
    @Query("delete from AiDocumentChunk c where c.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") Long documentId);

    /**
     * Lọc metadata TRƯỚC (trạng thái, loại, vùng, hiệu lực tại ngày asOf, cùng model embedding), sau đó mới xếp
     * theo khoảng cách cosine. score = 1 - cosine distance.
     */
    @Query(value = """
            SELECT c.id AS chunkId, c.document_id AS documentId, d.title AS title, c.content AS content,
                   1 - (c.embedding <=> CAST(:query AS vector)) AS score
            FROM ai_document_chunks c
            JOIN ai_documents d ON d.id = c.document_id
            WHERE d.status = 'ACTIVE'
              AND d.document_type IN (:types)
              AND (d.region = :region OR d.region = 'ALL')
              AND d.effective_date <= :asOf
              AND (d.expiry_date IS NULL OR d.expiry_date >= :asOf)
              AND c.embedding IS NOT NULL
              AND c.embedding_model = :model
            ORDER BY c.embedding <=> CAST(:query AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<ChunkSearchRow> searchSimilar(
            @Param("query") String query,
            @Param("types") Collection<String> types,
            @Param("region") String region,
            @Param("asOf") LocalDate asOf,
            @Param("model") String model,
            @Param("limit") int limit);

    /** Projection cho kết quả tìm kiếm vector. */
    interface ChunkSearchRow {
        Long getChunkId();

        Long getDocumentId();

        String getTitle();

        String getContent();

        Double getScore();
    }
}
