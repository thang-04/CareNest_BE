package com.carenest.dto.child;

import com.carenest.utils.enums.SmsStatus;

/** CP-21: mật khẩu tạm chỉ gửi qua SMS, người quản lý nhận trạng thái gửi. */
public record ParentPasswordResponse(Long userId, String username, SmsStatus smsStatus) {

    @Override
    public String toString() {
        return "ParentPasswordResponse[userId=" + userId + "]";
    }
}
