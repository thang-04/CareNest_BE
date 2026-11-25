package com.carenest.service.account.impl;

import com.carenest.exception.GlobalException;
import com.carenest.utils.ApiCode;

/** Kiểm trước các luật mật khẩu mà Keycloak chỉ báo chung một mã, để trả câu lỗi đúng nghĩa. */
final class PasswordRules {

    private PasswordRules() {}

    /** Mật khẩu không được trùng tên đăng nhập (SĐT); Keycloak `notUsername` vẫn là chốt chặn cuối. */
    static void rejectUsername(String password, String username) {
        if (password != null && username != null && password.equalsIgnoreCase(username)) {
            throw GlobalException.of(ApiCode.PASSWORD_SAME_AS_USERNAME);
        }
    }
}
