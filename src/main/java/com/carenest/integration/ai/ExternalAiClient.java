package com.carenest.integration.ai;

import com.carenest.integration.ai.dto.SummaryDraftContent;
import com.carenest.integration.ai.dto.SummaryDraftInput;
import com.carenest.integration.ai.dto.SummaryDraftInput.ReferenceSnippet;
import com.carenest.integration.ai.dto.SummaryDraftResult;
import com.carenest.utils.enums.AiErrorReason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

/**
 * Adapter LLM qua API tương thích OpenAI ({@code POST {baseUrl}/chat/completions}) — Groq, Gemini, OpenRouter…
 * Không log prompt, facts hay output (AI-05); chỉ log metadata.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "carenest.ai", name = "enabled", havingValue = "true")
public class ExternalAiClient implements AiClient {

    static final String SUMMARY_PROMPT = "summary/draft_v2";
    private static final double TEMPERATURE = 0.2;

    private final AiProperties.Llm llm;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptTemplateLoader;
    private final AiOutputGuard guard;
    private final RestClient restClient;

    @Autowired
    public ExternalAiClient(
            AiProperties properties, ObjectMapper objectMapper, PromptTemplateLoader promptTemplateLoader) {
        this(
                properties,
                objectMapper,
                promptTemplateLoader,
                RestClient.builder().requestFactory(requestFactory(properties.llm())));
    }

    // Dùng trong test để gắn MockRestServiceServer vào builder
    ExternalAiClient(
            AiProperties properties,
            ObjectMapper objectMapper,
            PromptTemplateLoader promptTemplateLoader,
            RestClient.Builder builder) {
        this.llm = properties.llm();
        this.objectMapper = objectMapper;
        this.promptTemplateLoader = promptTemplateLoader;
        this.guard = new AiOutputGuard(objectMapper);
        if (StringUtils.hasText(llm.baseUrl())) {
            builder.baseUrl(llm.baseUrl());
        }
        this.restClient = builder.build();
    }

    @Override
    public SummaryDraftResult draftChildSummary(SummaryDraftInput input) {
        requireConfigured();
        String systemPrompt = promptTemplateLoader.load(SUMMARY_PROMPT);
        String inputJson = objectMapper.writeValueAsString(input);
        String userMessage = buildUserMessage(input);

        int attempts = 1 + Math.max(0, llm.maxRetries());
        AiClientException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                String raw = complete(systemPrompt, userMessage);
                SummaryDraftContent content = guard.check(raw, input, inputJson);
                log.info(
                        "[draftChildSummary]|status=OK|model={}|prompt={}|attempt={}|latencyMs={}|references={}",
                        llm.model(),
                        SUMMARY_PROMPT,
                        attempt,
                        elapsedMillis(startedAt),
                        input.references() == null ? 0 : input.references().size());
                return new SummaryDraftResult(content, llm.provider(), llm.model(), SUMMARY_PROMPT);
            } catch (AiClientException ex) {
                lastError = ex;
                log.warn(
                        "[draftChildSummary]|status=FAILED|reason={}|model={}|attempt={}|latencyMs={}",
                        ex.getReason(),
                        llm.model(),
                        attempt,
                        elapsedMillis(startedAt));
                if (!isRetryable(ex.getReason()) || attempt == attempts) {
                    break;
                }
                sleep(llm.retryBackoff());
            }
        }
        throw lastError;
    }

    // Facts dạng JSON; tài liệu RAG bọc trong thẻ <reference> để prompt coi là bằng chứng, không phải chỉ dẫn
    private String buildUserMessage(SummaryDraftInput input) {
        SummaryDraftInput facts = new SummaryDraftInput(
                input.childRef(),
                input.ageMonths(),
                input.periodType(),
                input.periodFrom(),
                input.periodTo(),
                input.observedDays(),
                input.criteria(),
                input.weekly(),
                input.notes(),
                input.dataNotAvailable(),
                null);
        StringBuilder message = new StringBuilder("FACTS:\n").append(objectMapper.writeValueAsString(facts));
        List<ReferenceSnippet> references = input.references() == null ? List.of() : input.references();
        message.append("\n\nREFERENCES:\n");
        if (references.isEmpty()) {
            message.append("(none)");
        }
        for (ReferenceSnippet reference : references) {
            message.append("<reference id=\"")
                    .append(reference.chunkId())
                    .append("\" title=\"")
                    .append(sanitizeReference(reference.title()))
                    .append("\">\n")
                    .append(sanitizeReference(reference.text()))
                    .append("\n</reference>\n");
        }
        return message.toString();
    }

    private String complete(String systemPrompt, String userMessage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llm.model());
        body.put("temperature", TEMPERATURE);
        body.put("response_format", Map.of("type", "json_object"));
        // Chỉ gửi khi cấu hình: model suy luận (gpt-oss, Gemini) tốn ít token/quota hơn với "low"
        if (StringUtils.hasText(llm.reasoningEffort())) {
            body.put("reasoning_effort", llm.reasoningEffort());
        }
        body.put(
                "messages",
                List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)));
        ChatResponse response = call(
                "chatCompletion",
                () -> restClient
                        .post()
                        .uri("/chat/completions")
                        .headers(headers -> headers.setBearerAuth(llm.apiKey()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(ChatResponse.class));
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new AiClientException(AiErrorReason.OUTPUT_INVALID, "LLM returned no choices", null);
        }
        if (response.usage() != null) {
            log.info(
                    "[chatCompletion]|model={}|promptTokens={}|completionTokens={}",
                    llm.model(),
                    response.usage().promptTokens(),
                    response.usage().completionTokens());
        }
        return response.choices().getFirst().message().content();
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(llm.baseUrl()) || !StringUtils.hasText(llm.model())) {
            throw new AiClientException(AiErrorReason.DISABLED, "LLM base URL/model is not configured", null);
        }
    }

    static <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("[{}]|providerStatus={}", operation, status);
            AiErrorReason reason = status == HttpStatus.TOO_MANY_REQUESTS.value()
                    ? AiErrorReason.RATE_LIMITED
                    : AiErrorReason.PROVIDER_UNAVAILABLE;
            throw new AiClientException(reason, "AI provider " + operation + " failed with status " + status, ex);
        } catch (ResourceAccessException ex) {
            boolean timeout = ex.getCause() instanceof SocketTimeoutException;
            log.warn("[{}]|providerUnreachable|timeout={}", operation, timeout);
            throw new AiClientException(
                    timeout ? AiErrorReason.TIMEOUT : AiErrorReason.PROVIDER_UNAVAILABLE,
                    "AI provider " + operation + " unreachable",
                    ex);
        } catch (RestClientException ex) {
            log.warn("[{}]|providerError|type={}", operation, ex.getClass().getSimpleName());
            throw new AiClientException(AiErrorReason.PROVIDER_UNAVAILABLE, "AI provider " + operation + " failed", ex);
        }
    }

    // Không retry khi timeout (đã chờ đủ lâu) hoặc AI tắt
    private static boolean isRetryable(AiErrorReason reason) {
        return reason == AiErrorReason.RATE_LIMITED
                || reason == AiErrorReason.PROVIDER_UNAVAILABLE
                || reason == AiErrorReason.OUTPUT_INVALID;
    }

    // Chặn tài liệu tự đóng thẻ <reference> để chèn nội dung giả làm chỉ dẫn
    private static String sanitizeReference(String text) {
        return text == null ? "" : text.replace("<", "‹").replace(">", "›").replace("\"", "'");
    }

    private static void sleep(Duration backoff) {
        if (backoff == null || backoff.isZero() || backoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    private static SimpleClientHttpRequestFactory requestFactory(AiProperties.Llm llm) {
        return requestFactory(llm.connectTimeout(), llm.readTimeout());
    }

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
