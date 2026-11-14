package com.carenest.integration.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailGatewayTest {

    private JavaMailSender sender;
    private SmtpEmailGateway gateway;

    @BeforeEach
    void setUp() {
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        gateway = new SmtpEmailGateway(properties(""), sender);
    }

    @Test
    @DisplayName("Email: gửi html + text với From = username Gmail và tên hiển thị")
    void sendsMultipartFromSmtpUsername() throws Exception {
        gateway.send(new EmailMessage(List.of("parent@example.com"), "Thông báo", "Xin chào", "<p>Xin chào</p>"));

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        InternetAddress from = (InternetAddress) sent.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("team@example.com");
        assertThat(from.getPersonal()).isEqualTo("CareNest");
        assertThat(sent.getAllRecipients()).extracting(Object::toString).containsExactly("parent@example.com");
        assertThat(sent.getSubject()).isEqualTo("Thông báo");
    }

    @Test
    @DisplayName("Email: CARENEST_EMAIL_FROM được ưu tiên hơn username")
    void usesExplicitFromAddress() throws Exception {
        gateway = new SmtpEmailGateway(properties("alias@example.com"), sender);

        gateway.send(EmailMessage.text("parent@example.com", "Thông báo", "Xin chào"));

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        assertThat(((InternetAddress) captor.getValue().getFrom()[0]).getAddress())
                .isEqualTo("alias@example.com");
    }

    @Test
    @DisplayName("Email: Gmail từ chối đăng nhập ⇒ EmailDeliveryException nhắc App Password")
    void authenticationFailureMapsToDeliveryException() {
        doThrow(new MailAuthenticationException("535 Username and Password not accepted"))
                .when(sender)
                .send(any(MimeMessage.class));

        assertThatThrownBy(() -> gateway.send(EmailMessage.text("parent@example.com", "Tiêu đề", "Nội dung")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("App Password")
                .hasNoCause();
    }

    @Test
    @DisplayName("Email: lỗi gửi không lộ địa chỉ người nhận trong exception")
    void sendFailureDoesNotLeakRecipient() {
        doThrow(new MailSendException("Invalid Addresses parent@example.com"))
                .when(sender)
                .send(any(MimeMessage.class));

        assertThatThrownBy(() -> gateway.send(EmailMessage.text("parent@example.com", "Tiêu đề", "Nội dung")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasNoCause()
                .message()
                .doesNotContain("parent@example.com");
    }

    @Test
    @DisplayName("Email: thiếu người nhận hoặc nội dung ⇒ IllegalArgumentException trước khi gửi")
    void rejectsIncompleteMessage() {
        assertThatThrownBy(() -> new EmailMessage(List.of(" "), "Tiêu đề", "Nội dung", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailMessage.text("parent@example.com", "Tiêu đề", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EmailProperties properties(String from) {
        return new EmailProperties(
                EmailProperties.SMTP,
                from,
                "CareNest",
                new EmailProperties.Smtp(
                        "smtp.gmail.com",
                        587,
                        "team@example.com",
                        "app-password",
                        true,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10)));
    }
}
