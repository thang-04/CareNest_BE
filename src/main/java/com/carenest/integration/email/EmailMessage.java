package com.carenest.integration.email;

import java.util.List;

/**
 * Một email cần gửi. Cần ít nhất một trong {@code text}/{@code html}; có cả hai ⇒ gửi multipart (client không đọc được
 * HTML sẽ hiện bản text).
 */
public record EmailMessage(List<String> to, String subject, String text, String html) {

    public EmailMessage {
        to = to == null
                ? List.of()
                : to.stream().filter(EmailMessage::hasText).map(String::trim).toList();
        if (to.isEmpty()) {
            throw new IllegalArgumentException("Email cần ít nhất một người nhận");
        }
        if (!hasText(subject)) {
            throw new IllegalArgumentException("Email cần tiêu đề");
        }
        if (!hasText(text) && !hasText(html)) {
            throw new IllegalArgumentException("Email cần nội dung text hoặc html");
        }
    }

    public static EmailMessage text(String to, String subject, String text) {
        return new EmailMessage(List.of(to), subject, text, null);
    }

    public static EmailMessage html(String to, String subject, String html) {
        return new EmailMessage(List.of(to), subject, null, html);
    }

    boolean hasHtml() {
        return hasText(html);
    }

    // Không in địa chỉ và nội dung khi record bị log
    @Override
    public String toString() {
        return "EmailMessage[recipients=" + to.size() + ", html=" + hasHtml() + "]";
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
