package com.carenest.integration.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cổng đẩy giả khi chưa có Firebase: chỉ ghi log nền tảng và loại thông báo, không ghi token hay nội dung (có tên trẻ).
 * Thông báo vẫn được lưu để App đọc danh sách.
 */
@Slf4j
@Component
public class LoggingPushGateway implements PushGateway {

    @Override
    public void send(PushMessage message) {
        log.info(
                "[LoggingPushGateway]|send|platform={}|type={}",
                message.platform(),
                message.data().get("type"));
    }
}
