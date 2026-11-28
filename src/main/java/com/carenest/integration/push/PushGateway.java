package com.carenest.integration.push;

import com.carenest.utils.enums.DevicePlatform;
import java.util.Map;

/**
 * Cổng gửi thông báo đẩy tới một thiết bị: {@link FcmPushGateway} khi có khóa Firebase, ngược lại {@link LoggingPushGateway}
 * chỉ ghi log.
 */
public interface PushGateway {

    /** Gửi một thông báo; lỗi ném {@link PushException} đã phân loại. */
    void send(PushMessage message);

    /** {@code data}: khóa/giá trị cho App mở đúng màn hình (loại, id thông báo, id trẻ). */
    record PushMessage(String token, DevicePlatform platform, String title, String body, Map<String, String> data) {
        @Override
        public String toString() {
            return "PushMessage[platform=" + platform + "]";
        }
    }
}
