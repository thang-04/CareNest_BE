package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduRagChunk;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Vector pgvector chỉ đọc/ghi qua native query; vector truyền dạng chuỗi "[0.1,0.2,...]". */
public interface EduRagChunkRepository extends JpaRepository<EduRagChunk, String> {

    boolean existsBySource(String source);

    List<EduRagChunk> findByStatus(String status);

    List<EduRagChunk> findByStatusAndChunkTypeIn(String status, Collection<String> chunkTypes);

    @Modifying
    @Query(value = """
            UPDATE edu_rag_chunks
            SET model = :model, content_hash = :hash, embedding = CAST(:embedding AS vector), updated_at = now()
            WHERE id = :id
            """, nativeQuery = true)
    int updateEmbedding(
            @Param("id") String id,
            @Param("model") String model,
            @Param("hash") String hash,
            @Param("embedding") String embedding);

    /** Top {@code limit} đoạn ACTIVE đã có vector cùng model, xếp theo điểm giống (1 - khoảng cách cosine). */
    @Query(value = """
            SELECT c.id AS id, c.chunk_type AS chunkType, c.source AS source, c.content AS content,
                   CAST(c.meta AS text) AS meta, 1 - (c.embedding <=> CAST(:query AS vector)) AS sim
            FROM edu_rag_chunks c
            WHERE c.status = 'ACTIVE' AND c.model = :model AND c.embedding IS NOT NULL AND c.chunk_type IN (:types)
            ORDER BY c.embedding <=> CAST(:query AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<SimilarRow> similarTo(
            @Param("query") String query,
            @Param("model") String model,
            @Param("types") Collection<String> types,
            @Param("limit") int limit);

    interface SimilarRow {
        String getId();

        String getChunkType();

        String getSource();

        String getContent();

        String getMeta();

        Double getSim();
    }
}
