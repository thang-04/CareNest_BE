package com.carenest.integration.sms;

/** Gửi SMS không thành công; nơi gọi ghi trạng thái FAILED, không hủy nghiệp vụ đã lưu. */
public class SmsDeliveryException extends RuntimeException {

    public SmsDeliveryException(String message) {
        super(message);
    }

    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
