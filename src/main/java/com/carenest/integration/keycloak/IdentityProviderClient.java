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

    /** Gỡ realm role đang gán trực tiếp; user không có role đó thì bỏ qua. */
    void removeRealmRole(String userId, String roleName);

    /** Tên các realm role đang gán trực tiếp cho user (gồm cả role không thuộc CareNest). */
    List<String> getRealmRoleNames(String userId);

    void deleteUser(String userId);

    void setUserEnabled(String userId, boolean enabled);

    /**
     * Đổi tên đăng nhập (G94-180/AUTH-21: SĐT phụ huynh đổi được duyệt). Provider không đổi (realm cấm sửa username)
     * ⇒ {@link IdentityProviderException} REJECTED; trùng username ⇒ CONFLICT.
     */
    void updateUsername(String userId, String username);

    /** Đổi họ tên và email (null ⇒ giữ email cũ); email đổi thì bỏ trạng thái đã xác minh (AUTH-25, hồ sơ nhân sự). */
    void updateUserProfile(String userId, String fullName, String email);

    /**
     * Đặt họ tên, email đúng như DB (G94-180/AUTH-21, phụ huynh theo hồ sơ trẻ): email null ⇒ xóa email trên provider;
     * email đổi thì bỏ trạng thái đã xác minh. Khác {@link #updateUserProfile} ở cách hiểu email null.
     */
    void replaceProfile(String userId, String fullName, String email);

    /** Đặt mật khẩu tạm mới (bắt đổi ở lần đăng nhập kế tiếp). */
    void resetTemporaryPassword(String userId, String temporaryPassword);

    /** Thu hồi mọi session và refresh token của user. */
    void logoutUser(String userId);

    /** Gửi email đặt mật khẩu (yêu cầu user có email và SMTP đã cấu hình). */
    void sendSetupEmail(String userId);

    boolean canSendSetupEmail();
}
