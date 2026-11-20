package com.carenest.integration.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Cấu hình embedding (API tương thích OpenAI, vd. Gemini {@code gemini-embedding-001}). Chỉ nhận văn bản danh mục
 * công khai (tên món, loại, thực phẩm) và câu "mong muốn" của PHT — không dữ liệu trẻ (AI-04). {@code budget}: tổng
 * thời gian cho một lần embed lười trong lúc gợi ý; hết ngân sách thì bỏ phần còn lại, gợi ý chạy không vector.
 */
@ConfigurationProperties("carenest.embedding")
public record EmbeddingProperties(
        String baseUrl,
        String model,
        String apiKey,
        Integer dimensions,
        Duration connectTimeout,
        Duration readTimeout,
        Integer batchSize,
        Duration budget) {

    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);
    static final Duration DEFAULT_BUDGET = Duration.ofSeconds(8);
    static final int DEFAULT_BATCH_SIZE = 50;

    public EmbeddingProperties {
        dimensions = dimensions == null || dimensions < 0 ? 0 : dimensions;
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
        batchSize = batchSize == null || batchSize < 1 ? DEFAULT_BATCH_SIZE : batchSize;
        budget = budget == null ? DEFAULT_BUDGET : budget;
    }

    /** Thiếu base URL, model hoặc key ⇒ coi như không có embedding, không lỗi khởi động (máy dev không có key). */
    public boolean configured() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(model) && StringUtils.hasText(apiKey);
    }

    // Không để API key lọt vào log khi record bị in ra
    @Override
    public String toString() {
        return "EmbeddingProperties[baseUrl=" + baseUrl + ", model=" + model + ", dimensions=" + dimensions + "]";
    }
}
