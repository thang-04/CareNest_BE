package com.carenest.integration.keycloak;

/** temporaryPassword có thể null khi chỉ dùng email đặt mật khẩu. Không log record này. */
public record NewIdentityUser(String username, String email, String fullName, String temporaryPassword) {

    @Override
    public String toString() {
        return "NewIdentityUser[username=" + username + "]";
    }
}
