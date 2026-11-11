package com.carenest.integration.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adapter chat completions OpenAI-compatible: Gemini ({@code .../v1beta/openai}) và Groq ({@code .../openai/v1}) cùng
 * dùng được (AI-09). Chỉ log metadata: tác vụ, model, thời gian, kết quả (AI-05) — không log prompt/response.
 */
@Slf4j
@Component
public class OpenAiCompatibleAiClient implements AiClient {
    private static final String LOG_PREFIX = "[OpenAiCompatibleAiClient]";

    private final AiProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleAiClient(AiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (properties.connectTimeout() != null) factory.setConnectTimeout(properties.connectTimeout());
        if (properties.readTimeout() != null) factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.baseUrl() == null ? "" : properties.baseUrl())
                .build();
    }

    @Override
    public boolean isEnabled() {
        return properties.enabled();
    }

    @Override
    public AiResult complete(AiRequest request) {
        String logPrefix = LOG_PREFIX + "|complete|task=" + request.task() + "|model=" + properties.model();
        if (!isEnabled()) {
            log.info("{}|disabled", logPrefix);
            throw new AiException(AiException.Reason.UNAVAILABLE, "AI is not configured", null);
        }
        long started = System.nanoTime();
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.model(),
                    "temperature", 0.2,
                    "max_tokens", request.maxTokens(),
                    "messages",
                            List.of(
                                    Map.of("role", "system", "content", request.systemPrompt()),
                                    Map.of("role", "user", "content", request.userPrompt())));
            ChatResponse response = restClient
                    .post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
            String text = response == null
                            || response.choices() == null
                            || response.choices().isEmpty()
                    ? null
                    : response.choices().getFirst().message() == null
                            ? null
                            : response.choices().getFirst().message().content();
            if (text == null || text.isBlank()) {
                throw new AiException(AiException.Reason.UNAVAILABLE, "AI returned empty content", null);
            }
            String model = response.model() == null ? properties.model() : response.model();
            log.info("{}|latencyMs={}|result=ok", logPrefix, (System.nanoTime() - started) / 1_000_000);
            return new AiResult(text.strip(), model);
        } catch (ResourceAccessException ex) {
            boolean timeout = ex.getCause() instanceof SocketTimeoutException;
            log.warn(
                    "{}|latencyMs={}|result={}",
                    logPrefix,
                    (System.nanoTime() - started) / 1_000_000,
                    timeout ? "timeout" : "unreachable");
            throw new AiException(
                    timeout ? AiException.Reason.TIMEOUT : AiException.Reason.UNAVAILABLE, "AI call failed", ex);
        } catch (RestClientResponseException ex) {
            log.warn("{}|status={}|result=error", logPrefix, ex.getStatusCode().value());
            throw new AiException(AiException.Reason.UNAVAILABLE, "AI call failed", ex);
        } catch (RestClientException ex) {
            log.warn("{}|result=error|type={}", logPrefix, ex.getClass().getSimpleName());
            throw new AiException(AiException.Reason.UNAVAILABLE, "AI call failed", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(String model, List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {}
}
