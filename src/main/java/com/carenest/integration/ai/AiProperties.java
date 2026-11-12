package com.carenest.integration.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nhà cung cấp AI (AI-09: Gemini hoặc Groq) qua endpoint OpenAI-compatible. Thiếu khóa/model/base URL ⇒ AI tắt, nghiệp vụ
 * vẫn chạy (AI-03).
 */
@ConfigurationProperties("carenest.ai")
public record AiProperties(String baseUrl, String apiKey, String model, Duration connectTimeout, Duration readTimeout) {

    public boolean enabled() {
        return notBlank(baseUrl) && notBlank(apiKey) && notBlank(model);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    // Không để khóa API lọt vào log khi record bị in ra
    @Override
    public String toString() {
        return "AiProperties[baseUrl=" + baseUrl + ", model=" + model + ", enabled=" + enabled() + "]";
    }
}
