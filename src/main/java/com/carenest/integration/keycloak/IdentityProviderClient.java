package com.carenest.integration.keycloak;

import java.util.List;

/**
 * Thao tác quản trị danh tính (tạo/khóa tài khoản). Service nghiệp vụ chỉ dùng interface này, không gọi
 * Keycloak Admin API trực tiếp. Lỗi ném {@link IdentityProviderException}.
 */
public interface IdentityProviderClient {

    /** Tạo user, bắt đổi mật khẩu ở lần đăng nhập đầu; trả id user (= claim "sub"). */
    String createUser(NewIdentityUser user);

    void assignRealmRole(String userId, String roleName);

    /** Tên các realm role đang gán trực tiếp cho user (gồm cả role không thuộc CareNest). */
    List<String> getRealmRoleNames(String userId);

    void deleteUser(String userId);

    void setUserEnabled(String userId, boolean enabled);

    /** Đặt mật khẩu tạm mới (bắt đổi ở lần đăng nhập kế tiếp). */
    void resetTemporaryPassword(String userId, String temporaryPassword);

    /** Thu hồi mọi session và refresh token của user. */
    void logoutUser(String userId);

    /** Gửi email đặt mật khẩu (yêu cầu user có email và SMTP đã cấu hình). */
    void sendSetupEmail(String userId);

    boolean canSendSetupEmail();
}
