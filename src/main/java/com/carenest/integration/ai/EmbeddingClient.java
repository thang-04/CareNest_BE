package com.carenest.integration.ai;

import java.time.Duration;
import java.util.List;

/**
 * Port gọi embedding API (AI-06). Chỉ nhận văn bản danh mục công khai và câu mong muốn của PHT, không dữ liệu trẻ.
 * Mọi lỗi ném {@link AiClientException}; domain phải có luồng không vector (AI-03).
 */
public interface EmbeddingClient {

    /** AI bật và đủ base URL, model, key. */
    boolean isAvailable();

    /** Tên model, ghi cùng vector để biết vector nào cần embed lại khi đổi model. */
    String modelName();

    /** Ngân sách thời gian tối đa cho một lần embed lười trong lúc gợi ý. */
    Duration budget();

    /** Vector theo đúng thứ tự đầu vào; {@code timeout} là thời gian còn lại cho cả lần gọi (nhiều lô). */
    List<float[]> embed(List<String> texts, Duration timeout);
}
