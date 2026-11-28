package com.carenest.integration.ai;

import com.carenest.utils.enums.AiErrorReason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Embedding qua API tương thích OpenAI ({@code POST {baseUrl}/embeddings}). Gửi theo lô {@code batchSize}; {@code
 * dimensions > 0} thì gửi kèm và kiểm số chiều trả về. Không retry. Không log nội dung (AI-05).
 */
@Slf4j
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private static final String LOG_PREFIX = "[OpenAiCompatibleEmbeddingClient]";

    private final AiProperties aiProperties;
    private final EmbeddingProperties properties;
    // Tạo RestClient theo read timeout còn lại của ngân sách; test thay bằng client gắn MockRestServiceServer
    private final Function<Duration, RestClient> clientFactory;

    @Autowired
    public OpenAiCompatibleEmbeddingClient(AiProperties aiProperties, EmbeddingProperties properties) {
        this(
                aiProperties,
                properties,
                readTimeout -> RestClient.builder()
                        .baseUrl(properties.baseUrl() == null ? "" : properties.baseUrl())
                        .requestFactory(requestFactory(properties.connectTimeout(), readTimeout))
                        .build());
    }

    OpenAiCompatibleEmbeddingClient(
            AiProperties aiProperties, EmbeddingProperties properties, Function<Duration, RestClient> clientFactory) {
        this.aiProperties = aiProperties;
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @Override
    public boolean isAvailable() {
        return aiProperties.enabled() && properties.configured();
    }

    @Override
    public String modelName() {
        return properties.model();
    }

    @Override
    public Duration budget() {
        return properties.budget();
    }

    @Override
    public List<float[]> embed(List<String> texts, Duration timeout) {
        if (!isAvailable()) {
            throw new AiClientException(AiErrorReason.DISABLED, "Embedding is disabled or not configured", null);
        }
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        int batchSize = properties.batchSize();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            Duration remaining = Duration.ofNanos(deadlineNanos - System.nanoTime());
            if (remaining.isNegative() || remaining.isZero()) {
                throw new AiClientException(AiErrorReason.TIMEOUT, "Embedding budget exhausted", null);
            }
            vectors.addAll(embedOneBatch(texts.subList(from, Math.min(texts.size(), from + batchSize)), remaining));
        }
        return vectors;
    }

    private List<float[]> embedOneBatch(List<String> batch, Duration remaining) {
        Duration readTimeout = remaining.compareTo(properties.readTimeout()) < 0 ? remaining : properties.readTimeout();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("input", batch);
        if (properties.dimensions() > 0) {
            body.put("dimensions", properties.dimensions());
        }
        long startedAt = System.nanoTime();
        EmbeddingResponse response = send(clientFactory.apply(readTimeout), body);
        if (response == null || response.data() == null || response.data().size() != batch.size()) {
            throw new AiClientException(
                    AiErrorReason.OUTPUT_INVALID, "Embedding response size does not match input", null);
        }
        // Gemini (OpenAI-compat) có thể trả index = null: khi đó giữ thứ tự response, vốn đúng thứ tự input
        boolean indexed = response.data().stream().allMatch(data -> data.index() != null);
        List<float[]> vectors = (indexed
                        ? response.data().stream().sorted(Comparator.comparingInt(EmbeddingData::index))
                        : response.data().stream())
                .map(EmbeddingData::embedding)
                .toList();
        for (float[] vector : vectors) {
            boolean wrongSize = vector == null
                    || vector.length == 0
                    || (properties.dimensions() > 0 && vector.length != properties.dimensions());
            if (wrongSize) {
                throw new AiClientException(
                        AiErrorReason.OUTPUT_INVALID, "Embedding dimensions do not match configuration", null);
            }
        }
        log.info(
                "{}|status=OK|model={}|batch={}|latencyMs={}",
                LOG_PREFIX,
                properties.model(),
                batch.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return vectors;
    }

    private EmbeddingResponse send(RestClient client, Map<String, Object> body) {
        try {
            return client.post()
                    .uri("/embeddings")
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(EmbeddingResponse.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            AiErrorReason reason = status == HttpStatus.TOO_MANY_REQUESTS.value()
                    ? AiErrorReason.RATE_LIMITED
                    : AiErrorReason.PROVIDER_UNAVAILABLE;
            throw new AiClientException(reason, "Embedding failed with status " + status, ex);
        } catch (ResourceAccessException ex) {
            Throwable cause = ex.getCause();
            boolean timeout = cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException;
            throw new AiClientException(
                    timeout ? AiErrorReason.TIMEOUT : AiErrorReason.PROVIDER_UNAVAILABLE,
                    "Embedding provider unreachable",
                    ex);
        } catch (RestClientException ex) {
            throw new AiClientException(AiErrorReason.PROVIDER_UNAVAILABLE, "Embedding failed", ex);
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<EmbeddingData> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(Integer index, float[] embedding) {}
}
