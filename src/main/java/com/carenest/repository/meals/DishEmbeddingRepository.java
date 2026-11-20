package com.carenest.repository.meals;

import com.carenest.entity.meals.DishEmbedding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Vector pgvector chỉ đọc/ghi qua native query; vector truyền dạng chuỗi "[0.1,0.2,...]". */
public interface DishEmbeddingRepository extends JpaRepository<DishEmbedding, Long> {

    List<DishEmbedding> findByModel(String model);

    @Modifying
    @Query(value = """
            INSERT INTO dish_embeddings (dish_id, model, content_hash, embedding, updated_at)
            VALUES (:dishId, :model, :hash, CAST(:embedding AS vector), now())
            ON CONFLICT (dish_id) DO UPDATE
            SET model = EXCLUDED.model, content_hash = EXCLUDED.content_hash,
                embedding = EXCLUDED.embedding, updated_at = now()
            """, nativeQuery = true)
    int upsert(
            @Param("dishId") Long dishId,
            @Param("model") String model,
            @Param("hash") String hash,
            @Param("embedding") String embedding);

    /** Điểm giống (1 - khoảng cách cosine) của mọi món đã có vector cùng model so với vector truy vấn. */
    @Query(value = """
            SELECT e.dish_id AS dishId, 1 - (e.embedding <=> CAST(:query AS vector)) AS score
            FROM dish_embeddings e
            WHERE e.model = :model
            """, nativeQuery = true)
    List<ScoreRow> similarTo(@Param("query") String query, @Param("model") String model);

    /** Như {@link #similarTo} nhưng truy vấn là vector đã lưu của một món (thay một món ⇒ món gần món cũ). */
    @Query(value = """
            SELECT e.dish_id AS dishId, 1 - (e.embedding <=> f.embedding) AS score
            FROM dish_embeddings e
            JOIN dish_embeddings f ON f.dish_id = :fromDishId AND f.model = e.model
            WHERE e.model = :model AND e.dish_id <> :fromDishId
            """, nativeQuery = true)
    List<ScoreRow> similarToDish(@Param("fromDishId") Long fromDishId, @Param("model") String model);

    interface ScoreRow {
        Long getDishId();

        Double getScore();
    }
}
