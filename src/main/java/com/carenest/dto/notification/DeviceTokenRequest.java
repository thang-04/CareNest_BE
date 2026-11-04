package com.carenest.dto.notification;

import com.carenest.utils.enums.DevicePlatform;

/** Thiết bị nhận thông báo đẩy: token của dịch vụ đẩy (tối đa 512 ký tự); {@code platform} bắt buộc khi đăng ký. */
public record DeviceTokenRequest(String token, DevicePlatform platform) {
    @Override
    public String toString() {
        return "DeviceTokenRequest[platform=" + platform + "]";
    }
}
