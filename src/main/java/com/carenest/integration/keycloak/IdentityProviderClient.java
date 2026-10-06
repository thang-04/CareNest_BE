package com.carenest.integration.keycloak;

/**
 * Thao tác quản trị danh tính (tạo/khóa tài khoản). Service nghiệp vụ chỉ dùng interface này, không gọi
 * Keycloak Admin API trực tiếp. Lỗi ném {@link IdentityProviderException}.
 */
public interface IdentityProviderClient {

    /** Tạo user, bắt đổi mật khẩu ở lần đăng nhập đầu; trả id user (= claim "sub"). */
    String createUser(NewIdentityUser user);

    void assignRealmRole(String userId, String roleName);

    void deleteUser(String userId);

    void setUserEnabled(String userId, boolean enabled);

    /** Thu hồi mọi session và refresh token của user. */
    void logoutUser(String userId);

    /** Gửi email đặt mật khẩu (yêu cầu user có email và SMTP đã cấu hình). */
    void sendSetupEmail(String userId);

    boolean canSendSetupEmail();
}
