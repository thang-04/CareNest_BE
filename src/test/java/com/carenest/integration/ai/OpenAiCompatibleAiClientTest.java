package com.carenest.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
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
}
