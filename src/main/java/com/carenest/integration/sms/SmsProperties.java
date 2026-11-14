package com.carenest.integration.sms;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Chọn nhà cung cấp SMS (D-2 A: mặc định cổng giả). textbee gửi bằng SIM điện thoại Android, chỉ dùng dev/demo. */
@ConfigurationProperties("carenest.sms")
public record SmsProperties(String provider, Textbee textbee) {

    public static final String FAKE = "fake";
    public static final String TEXTBEE = "textbee";

    /** true khi tin được gửi tới điện thoại thật (không phải cổng giả). */
    public boolean usesRealProvider() {
        return provider != null && !provider.isBlank() && !FAKE.equalsIgnoreCase(provider.trim());
    }

    public record Textbee(
            String baseUrl, String apiKey, String deviceId, Duration connectTimeout, Duration readTimeout) {

        // Không để API key lọt vào log khi record bị in ra
        @Override
        public String toString() {
            return "Textbee[baseUrl=" + baseUrl + ", deviceId=" + deviceId + "]";
        }
    }
}
