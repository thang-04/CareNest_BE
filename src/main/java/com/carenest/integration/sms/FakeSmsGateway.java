package com.carenest.integration.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cổng SMS giả (D-2 A): không gửi thật, coi như đã gửi. Chỉ log 2 số cuối của SĐT và độ dài tin, không log nội dung
 * (có mật khẩu tạm). Môi trường dev: đặt lại mật khẩu phụ huynh trên Keycloak admin nếu cần đăng nhập thử.
 */
@Slf4j
@Component
public class FakeSmsGateway implements SmsGateway {

    @Override
    public void send(String phone, String text) {
        String tail = phone == null || phone.length() < 2 ? "" : phone.substring(phone.length() - 2);
        log.info("[FakeSmsGateway]|send|phone=***{}|length={}|SENT", tail, text == null ? 0 : text.length());
    }
}
