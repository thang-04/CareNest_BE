package com.carenest.dto.child;

/** Mật khẩu tạm mới hệ thống sinh cho phụ huynh; chỉ trả một lần (AUTH-19). */
public record ParentPasswordResponse(Long userId, String username, String temporaryPassword) {

    @Override
    public String toString() {
        return "ParentPasswordResponse[userId=" + userId + "]";
    }
}
