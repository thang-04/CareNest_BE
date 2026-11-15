package com.carenest.integration.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Gửi email qua SMTP (mặc định Gmail smtp.gmail.com:587 STARTTLS). Bật khi {@code carenest.email.provider=smtp}.
 * Tự tạo {@link JavaMailSenderImpl} thay vì bean {@code spring.mail.*}: không sinh health check SMTP làm actuator DOWN
 * khi Gmail chậm, và báo thiếu cấu hình ngay lúc khởi động. Không log địa chỉ, tiêu đề, nội dung, mật khẩu.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "carenest.email.provider", havingValue = EmailProperties.SMTP)
public class SmtpEmailGateway implements EmailGateway {

    private final JavaMailSender sender;
    private final String fromAddress;
    private final String fromName;

    @Autowired
    public SmtpEmailGateway(EmailProperties properties) {
        this(properties, mailSender(requireComplete(properties)));
    }

    SmtpEmailGateway(EmailProperties properties, JavaMailSender sender) {
        requireComplete(properties);
        this.sender = sender;
        this.fromAddress = properties.senderAddress();
        this.fromName = properties.fromName();
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = sender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, message.hasHtml(), StandardCharsets.UTF_8.name());
            if (EmailMessage.hasText(fromName)) {
                helper.setFrom(fromAddress, fromName);
            } else {
                helper.setFrom(fromAddress);
            }
            helper.setTo(message.to().toArray(String[]::new));
            helper.setSubject(message.subject());
            if (message.hasHtml() && EmailMessage.hasText(message.text())) {
                helper.setText(message.text(), message.html());
            } else if (message.hasHtml()) {
                helper.setText(message.html(), true);
            } else {
                helper.setText(message.text(), false);
            }
            sender.send(mime);
        } catch (MailAuthenticationException ex) {
            log.warn("[SmtpEmailGateway]|send|type=MailAuthenticationException|FAILED");
            // Gmail trả 535 khi dùng mật khẩu đăng nhập thay vì App Password
            throw new EmailDeliveryException("SMTP từ chối đăng nhập: kiểm tra username và App Password");
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.warn("[SmtpEmailGateway]|send|type={}|FAILED", ex.getClass().getSimpleName());
            // Không giữ cause: thông báo lỗi SMTP có thể chứa địa chỉ người nhận
            throw new EmailDeliveryException(
                    "Không gửi được email: " + ex.getClass().getSimpleName());
        }
        log.info(
                "[SmtpEmailGateway]|send|recipients={}|html={}|SENT",
                message.to().size(),
                message.hasHtml());
    }

    // Chọn smtp mà thiếu cấu hình ⇒ dừng lúc khởi động, không đợi tới lần gửi đầu
    private static EmailProperties requireComplete(EmailProperties properties) {
        EmailProperties.Smtp smtp = properties.smtp();
        List<String> missing = new ArrayList<>();
        if (smtp == null || !EmailMessage.hasText(smtp.host())) missing.add("CARENEST_EMAIL_SMTP_HOST");
        if (smtp == null || smtp.port() == null) missing.add("CARENEST_EMAIL_SMTP_PORT");
        boolean hasUser = smtp != null && EmailMessage.hasText(smtp.username());
        if (hasUser && !EmailMessage.hasText(smtp.password())) missing.add("CARENEST_EMAIL_SMTP_PASSWORD");
        if (!EmailMessage.hasText(properties.senderAddress())) {
            missing.add("CARENEST_EMAIL_FROM hoặc CARENEST_EMAIL_SMTP_USERNAME");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "CARENEST_EMAIL_PROVIDER=smtp nhưng thiếu cấu hình: " + String.join(", ", missing));
        }
        return properties;
    }

    private static JavaMailSender mailSender(EmailProperties properties) {
        EmailProperties.Smtp smtp = properties.smtp();
        var impl = new JavaMailSenderImpl();
        impl.setHost(smtp.host());
        impl.setPort(smtp.port());
        impl.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties props = impl.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        if (EmailMessage.hasText(smtp.username())) {
            impl.setUsername(smtp.username());
            impl.setPassword(smtp.password());
            props.put("mail.smtp.auth", "true");
        }
        if (Boolean.TRUE.equals(smtp.starttls())) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        if (smtp.connectTimeout() != null) {
            props.put(
                    "mail.smtp.connectiontimeout",
                    String.valueOf(smtp.connectTimeout().toMillis()));
        }
        if (smtp.readTimeout() != null) {
            props.put("mail.smtp.timeout", String.valueOf(smtp.readTimeout().toMillis()));
            props.put(
                    "mail.smtp.writetimeout", String.valueOf(smtp.readTimeout().toMillis()));
        }
        return impl;
    }
}
