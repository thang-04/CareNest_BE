package com.carenest.integration.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
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
    private static final String TRUNCATED = "AI output truncated";

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
            Map<String, Object> body = requestBody(request, properties.model());
            ChatResponse response = restClient
                    .post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            Integer completionTokens = response == null || response.usage() == null
                    ? null
                    : response.usage().completionTokens();
            String text;
            try {
                text = contentOf(response);
            } catch (AiException ex) {
                log.warn(
                        "{}|latencyMs={}|completionTokens={}|result={}",
                        logPrefix,
                        latencyMs,
                        completionTokens,
                        TRUNCATED.equals(ex.getMessage()) ? "truncated" : "empty");
                throw ex;
            }
            String model = response.model() == null ? properties.model() : response.model();
            log.info("{}|latencyMs={}|completionTokens={}|result=ok", logPrefix, latencyMs, completionTokens);
            return new AiResult(text, model);
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

    /** Body chat completions; {@code jsonOutput} ⇒ {@code response_format: json_object} (Gemini, Groq đều nhận). */
    static Map<String, Object> requestBody(AiRequest request, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", request.maxTokens());
        body.put(
                "messages",
                List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())));
        if (request.jsonOutput()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    /** Nội dung trả lời; bị cắt vì hết {@code max_tokens} hoặc rỗng ⇒ UNAVAILABLE (bản cắt dở không dùng được). */
    static String contentOf(ChatResponse response) {
        Choice choice = response == null
                        || response.choices() == null
                        || response.choices().isEmpty()
                ? null
                : response.choices().getFirst();
        if (choice != null && "length".equals(choice.finishReason())) {
            throw new AiException(AiException.Reason.UNAVAILABLE, TRUNCATED, null);
        }
        String text = choice == null || choice.message() == null
                ? null
                : choice.message().content();
        if (text == null || text.isBlank()) {
            throw new AiException(AiException.Reason.UNAVAILABLE, "AI returned empty content", null);
        }
        return text.strip();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(String model, List<Choice> choices, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(
            Message message, @JsonProperty("finish_reason") String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("completion_tokens") Integer completionTokens) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {}
}
