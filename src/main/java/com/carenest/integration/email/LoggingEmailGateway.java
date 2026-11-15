package com.carenest.integration.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Cổng email giả: không gửi, chỉ log số người nhận và loại nội dung (không log địa chỉ, tiêu đề, nội dung). */
@Slf4j
@Component
@ConditionalOnProperty(name = "carenest.email.provider", havingValue = EmailProperties.LOG, matchIfMissing = true)
public class LoggingEmailGateway implements EmailGateway {

    @Override
    public void send(EmailMessage message) {
        log.info(
                "[LoggingEmailGateway]|send|recipients={}|html={}|SENT",
                message.to().size(),
                message.hasHtml());
    }
}
