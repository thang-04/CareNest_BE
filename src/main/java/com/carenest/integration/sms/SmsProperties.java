package com.carenest.integration.sms;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Chọn nhà cung cấp SMS (D-2 A: mặc định cổng giả). Twilio bản trial chỉ dùng dev/demo. */
@ConfigurationProperties("carenest.sms")
public record SmsProperties(String provider, Twilio twilio) {

    public static final String FAKE = "fake";
    public static final String TWILIO = "twilio";

    /** true khi tin được gửi tới điện thoại thật (không phải cổng giả). */
    public boolean usesRealProvider() {
        return provider != null && !provider.isBlank() && !FAKE.equalsIgnoreCase(provider.trim());
    }

    public record Twilio(
            String accountSid,
            String authToken,
            String from,
            String messagingServiceSid,
            Duration connectTimeout,
            Duration readTimeout) {

        // Không để auth token lọt vào log khi record bị in ra
        @Override
        public String toString() {
            return "Twilio[accountSid=" + accountSid + ", from=" + from + ", messagingServiceSid=" + messagingServiceSid
                    + "]";
        }
    }
}
