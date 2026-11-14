package com.carenest.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleAiClientTest {

    @Test
    @DisplayName("AI-03: thiếu khóa API ⇒ AI tắt, gọi ⇒ UNAVAILABLE (không gọi mạng); toString không lộ khóa")
    void disabledWithoutKey() {
        AiProperties properties = new AiProperties(
                "https://example.invalid/v1", " ", "model-x", Duration.ofSeconds(1), Duration.ofSeconds(1));
        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(properties);

        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.complete(new AiClient.AiRequest("t", "s", "u", 10)))
                .isInstanceOfSatisfying(
                        AiException.class, ex -> assertThat(ex.getReason()).isEqualTo(AiException.Reason.UNAVAILABLE));
        assertThat(new AiProperties("u", "secret-key", "m", null, null).toString())
                .doesNotContain("secret-key");
    }

    @Test
    @DisplayName("AI-06: jsonOutput ⇒ body có response_format json_object; mặc định không có")
    void jsonOutputAddsResponseFormat() {
        Map<String, Object> json =
                OpenAiCompatibleAiClient.requestBody(new AiClient.AiRequest("t", "s", "u", 1200, true), "model-x");
        assertThat(json).containsEntry("response_format", Map.of("type", "json_object"));
        assertThat(json).containsEntry("max_tokens", 1200).containsEntry("model", "model-x");

        Map<String, Object> text =
                OpenAiCompatibleAiClient.requestBody(new AiClient.AiRequest("t", "s", "u", 400), "model-x");
        assertThat(text).doesNotContainKey("response_format");
    }

    @Test
    @DisplayName(
            "AI-03: output bị cắt vì hết max_tokens (finish_reason=length) ⇒ UNAVAILABLE; output đủ ⇒ trả nội dung")
    void truncatedOutputIsUnavailable() {
        OpenAiCompatibleAiClient.ChatResponse truncated = new OpenAiCompatibleAiClient.ChatResponse(
                "m",
                List.of(new OpenAiCompatibleAiClient.Choice(
                        new OpenAiCompatibleAiClient.Message("{\"overview\": \"Tre"), "length")),
                null);
        assertThatThrownBy(() -> OpenAiCompatibleAiClient.contentOf(truncated))
                .isInstanceOfSatisfying(
                        AiException.class, ex -> assertThat(ex.getReason()).isEqualTo(AiException.Reason.UNAVAILABLE));

        OpenAiCompatibleAiClient.ChatResponse done = new OpenAiCompatibleAiClient.ChatResponse(
                "m",
                List.of(new OpenAiCompatibleAiClient.Choice(new OpenAiCompatibleAiClient.Message(" {} "), "stop")),
                new OpenAiCompatibleAiClient.Usage(12));
        assertThat(OpenAiCompatibleAiClient.contentOf(done)).isEqualTo("{}");
    }
}
