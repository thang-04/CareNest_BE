package com.carenest.integration.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình AI (ADR-0007). LLM và embedding là 2 provider độc lập, cùng chuẩn API tương thích OpenAI;
 * đổi provider chỉ cần đổi env, không sửa code.
 */
@ConfigurationProperties("carenest.ai")
public record AiProperties(boolean enabled, Llm llm, Embedding embedding, Rag rag) {

    public record Llm(
            String provider,
            String baseUrl,
            String model,
            String apiKey,
            Duration connectTimeout,
            Duration readTimeout,
            int maxRetries,
            Duration retryBackoff,
            String reasoningEffort) {

        // Không để API key lọt vào log khi record bị in ra
        @Override
        public String toString() {
            return "Llm[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model + "]";
        }
    }

    public record Embedding(
            String provider,
            String baseUrl,
            String model,
            String apiKey,
            int dimensions,
            int batchSize,
            Duration connectTimeout,
            Duration readTimeout) {

        @Override
        public String toString() {
            return "Embedding[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model + ", dimensions="
                    + dimensions + "]";
        }
    }

    /** topK: số chunk tối đa đưa vào prompt; minScore: ngưỡng cosine similarity (0..1), cần hiệu chỉnh theo dữ liệu; region: vùng của trường. */
    public record Rag(int topK, double minScore, String region) {}
}
