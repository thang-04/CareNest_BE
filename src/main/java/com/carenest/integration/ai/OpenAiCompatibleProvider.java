package com.carenest.integration.ai;

import com.carenest.utils.enums.AiErrorReason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Một provider qua API tương thích OpenAI ({@code POST {baseUrl}/chat/completions}) — Groq, Gemini (endpoint
 * {@code /v1beta/openai}). Không retry: chuỗi provider quyết định thử tiếp. Không log nội dung (AI-05).
 */
class OpenAiCompatibleProvider {

    private static final double TEMPERATURE = 0.2;

    private final AiProperties.Provider config;
    // Tạo RestClient theo read timeout còn lại của deadline; test thay bằng client gắn MockRestServiceServer
    private final Function<Duration, RestClient> clientFactory;

    OpenAiCompatibleProvider(AiProperties.Provider config) {
        this(
                config,
                readTimeout -> RestClient.builder()
                        .baseUrl(config.baseUrl())
                        .requestFactory(requestFactory(config.connectTimeout(), readTimeout))
                        .build());
    }

    OpenAiCompatibleProvider(AiProperties.Provider config, Function<Duration, RestClient> clientFactory) {
        this.config = config;
        this.clientFactory = clientFactory;
    }

    String id() {
        return config.id();
    }

    String model() {
        return config.model();
    }

    boolean configured() {
        return config.configured();
    }

    boolean mayTrainOnData() {
        return config.mayTrainOnData();
    }

    Completion complete(String systemPrompt, String userPrompt, Duration remaining) {
        Duration readTimeout = remaining.compareTo(config.readTimeout()) < 0 ? remaining : config.readTimeout();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("temperature", TEMPERATURE);
        body.put("response_format", Map.of("type", "json_object"));
        // Model suy luận (gpt-oss, Gemini 2.5) tốn ít token/quota hơn với "low"; chỉ gửi khi cấu hình
        if (StringUtils.hasText(config.reasoningEffort())) {
            body.put("reasoning_effort", config.reasoningEffort());
        }
        body.put(
                "messages",
                List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        ChatResponse response = send(clientFactory.apply(readTimeout), body);
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || !StringUtils.hasText(response.choices().getFirst().message().content())) {
            throw new AiClientException(
                    AiErrorReason.OUTPUT_INVALID, "Provider " + id() + " returned no content", null);
        }
        Usage usage = response.usage();
        return new Completion(
                response.choices().getFirst().message().content(),
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens());
    }

    private ChatResponse send(RestClient client, Map<String, Object> body) {
        try {
            return client.post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(config.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            AiErrorReason reason = status == HttpStatus.TOO_MANY_REQUESTS.value()
                    ? AiErrorReason.RATE_LIMITED
                    : AiErrorReason.PROVIDER_UNAVAILABLE;
            throw new AiClientException(reason, "Provider " + id() + " failed with status " + status, ex);
        } catch (ResourceAccessException ex) {
            Throwable cause = ex.getCause();
            boolean timeout = cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException;
            throw new AiClientException(
                    timeout ? AiErrorReason.TIMEOUT : AiErrorReason.PROVIDER_UNAVAILABLE,
                    "Provider " + id() + " unreachable",
                    ex);
        } catch (RestClientException ex) {
            throw new AiClientException(AiErrorReason.PROVIDER_UNAVAILABLE, "Provider " + id() + " failed", ex);
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    record Completion(String content, Integer inputTokens, Integer outputTokens) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens) {}
}
