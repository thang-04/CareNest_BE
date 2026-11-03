package com.carenest.integration.sms;

/**
 * Gửi SMS tới phụ huynh (L-15). D-2 A: dùng cổng giả {@link FakeSmsGateway} trước, chọn nhà cung cấp thật trước khi đưa
 * vào dùng (thay bean này). Tin có thể chứa mật khẩu tạm: nơi gọi và bản cài đặt không log nội dung tin.
 */
public interface SmsGateway {

    /** Gửi một tin tới SĐT 10 số bắt đầu 0; lỗi ném {@link SmsDeliveryException}. */
    void send(String phone, String text);
}
