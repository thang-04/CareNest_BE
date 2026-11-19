package com.carenest.entity.meals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Dấu vết embedding của món (AI-06). Cột {@code embedding} kiểu pgvector không ánh xạ vào entity: chỉ ghi/đọc qua
 * native query ở repository; entity dùng để biết món nào cần embed lại (khác model hoặc khác hash nội dung).
 */
@Getter
@Entity
@Table(name = "dish_embeddings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DishEmbedding {

    @Id
    @Column(name = "dish_id")
    private Long dishId;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
