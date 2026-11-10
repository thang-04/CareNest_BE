package com.carenest.integration.ai;

import com.carenest.utils.enums.AiErrorReason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Adapter embedding qua API tương thích OpenAI ({@code POST {baseUrl}/embeddings}). Gửi theo lô
 * {@code batchSize}; {@code dimensions > 0} thì gửi kèm và kiểm tra số chiều trả về.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "carenest.ai", name = "enabled", havingValue = "true")
public class ExternalEmbeddingClient implements EmbeddingClient {

    private final AiProperties.Embedding embedding;
    private final RestClient restClient;

    @Autowired
    public ExternalEmbeddingClient(AiProperties properties) {
        this(
                properties,
                RestClient.builder()
                        .requestFactory(ExternalAiClient.requestFactory(
                                properties.embedding().connectTimeout(),
                                properties.embedding().readTimeout())));
    }

    // Dùng trong test để gắn MockRestServiceServer vào builder
    ExternalEmbeddingClient(AiProperties properties, RestClient.Builder builder) {
        this.embedding = properties.embedding();
        if (StringUtils.hasText(embedding.baseUrl())) {
            builder.baseUrl(embedding.baseUrl());
        }
        this.restClient = builder.build();
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (!StringUtils.hasText(embedding.baseUrl()) || !StringUtils.hasText(embedding.model())) {
            throw new AiClientException(AiErrorReason.DISABLED, "Embedding base URL/model is not configured", null);
        }
        int batchSize = Math.max(1, embedding.batchSize());
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            vectors.addAll(embedOneBatch(texts.subList(from, Math.min(texts.size(), from + batchSize))));
        }
        return vectors;
    }

    @Override
    public String modelName() {
        return embedding.model();
    }

    private List<float[]> embedOneBatch(List<String> batch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embedding.model());
        body.put("input", batch);
        if (embedding.dimensions() > 0) {
            body.put("dimensions", embedding.dimensions());
        }
        long startedAt = System.nanoTime();
        EmbeddingResponse response = ExternalAiClient.call(
                "embeddings",
                () -> restClient
                        .post()
                        .uri("/embeddings")
                        .headers(headers -> headers.setBearerAuth(embedding.apiKey()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(EmbeddingResponse.class));
        if (response == null || response.data() == null || response.data().size() != batch.size()) {
            throw new AiClientException(
                    AiErrorReason.OUTPUT_INVALID, "Embedding response size does not match input", null);
        }
        // Gemini (OpenAI-compat) trả index = null: khi đó giữ nguyên thứ tự response, vốn đúng thứ tự input
        boolean indexed = response.data().stream().allMatch(data -> data.index() != null);
        List<float[]> vectors = (indexed
                        ? response.data().stream().sorted(Comparator.comparingInt(EmbeddingData::index))
                        : response.data().stream())
                .map(EmbeddingData::embedding)
                .toList();
        for (float[] vector : vectors) {
            boolean wrongSize = vector == null
                    || vector.length == 0
                    || (embedding.dimensions() > 0 && vector.length != embedding.dimensions());
            if (wrongSize) {
                throw new AiClientException(
                        AiErrorReason.OUTPUT_INVALID, "Embedding dimensions do not match configuration", null);
            }
        }
        log.info(
                "[embeddings]|status=OK|model={}|batch={}|latencyMs={}",
                embedding.model(),
                batch.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return vectors;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<EmbeddingData> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(Integer index, float[] embedding) {}
}
