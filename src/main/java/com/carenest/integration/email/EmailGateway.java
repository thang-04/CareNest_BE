package com.carenest.integration.email;

/**
 * Gửi email từ BE. Mặc định {@link LoggingEmailGateway} (không gửi thật); {@code carenest.email.provider=smtp} ⇒
 * {@link SmtpEmailGateway} (Gmail hoặc SMTP khác). Gửi đồng bộ: nơi gọi tự quyết gửi sau commit/bất đồng bộ nếu cần.
 */
public interface EmailGateway {

    /** Gửi một email; lỗi ném {@link EmailDeliveryException}. */
    void send(EmailMessage message);
}
