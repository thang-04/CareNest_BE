package com.carenest.integration.email;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Chọn cách gửi email: {@code log} (mặc định, không gửi thật) hoặc {@code smtp} (mặc định Gmail). */
@ConfigurationProperties("carenest.email")
public record EmailProperties(String provider, String from, String fromName, Smtp smtp) {

    public static final String LOG = "log";
    public static final String SMTP = "smtp";

    /** Địa chỉ gửi: {@code from} nếu có, không thì username SMTP (Gmail bắt buộc gửi từ chính tài khoản/alias). */
    public String senderAddress() {
        if (EmailMessage.hasText(from)) return from.trim();
        return smtp == null ? null : smtp.username();
    }

    public record Smtp(
            String host,
            Integer port,
            String username,
            String password,
            Boolean starttls,
            Duration connectTimeout,
            Duration readTimeout) {

        // Không để App Password lọt vào log khi record bị in ra
        @Override
        public String toString() {
            return "Smtp[host=" + host + ", port=" + port + ", username=" + username + ", starttls=" + starttls + "]";
        }
    }
}
