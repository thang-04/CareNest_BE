package com.carenest.integration.push;

import com.carenest.utils.enums.DevicePlatform;
import java.util.Map;

/**
 * Cổng gửi thông báo đẩy tới một thiết bị. Bản hiện tại là cổng giả {@link LoggingPushGateway}; gắn Firebase Cloud
 * Messaging khi có project và thông tin xác thực (không để trong repo).
 */
public interface PushGateway {

    /** Gửi một thông báo; lỗi do nơi gọi ghi log, không làm hỏng việc đã lưu. */
    void send(PushMessage message);

    /** {@code data}: khóa/giá trị cho App mở đúng màn hình (loại, id thông báo, id trẻ). */
    record PushMessage(String token, DevicePlatform platform, String title, String body, Map<String, String> data) {
        @Override
        public String toString() {
            return "PushMessage[platform=" + platform + "]";
        }
    }
}
