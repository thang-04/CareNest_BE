package com.carenest.service.account;

import com.carenest.dto.account.AuthRequests;
import com.carenest.dto.account.AuthResponses;
import com.carenest.security.CurrentUser;

public interface AuthService {

    /** Cookie phiên đã mã hóa; maxAgeSeconds âm = cookie theo phiên trình duyệt. */
    record SessionCookie(String value, long maxAgeSeconds) {
        @Override
        public String toString() {
            return "SessionCookie[redacted]";
        }
    }

    /** {@code data.takeover() != null} ⇒ {@code cookie} là cookie lượt chờ xác nhận (AUTH-28), chưa phải cookie phiên. */
    record LoginResult(AuthResponses.LoginData data, SessionCookie cookie) {
        @Override
        public String toString() {
            return "LoginResult[redacted]";
        }
    }

    record RefreshResult(AuthResponses.AccessToken data, SessionCookie cookie) {
        @Override
        public String toString() {
            return "RefreshResult[redacted]";
        }
    }

    AuthResponses.Csrf csrf(String token);

    LoginResult login(AuthRequests.Login request);

    /** AUTH-28: trình duyệt mới chọn "Dùng ở đây" ⇒ cấp phiên và hủy phiên Web khác. */
    LoginResult confirmTakeover(String takeoverCookie);

    /** AUTH-28: trình duyệt mới chọn "Hủy" ⇒ bỏ lượt chờ, phiên đang dùng giữ nguyên. */
    void cancelTakeover(String takeoverCookie);

    /** AUTH-28: phiên đang dùng hỏi có lượt đăng nhập nào ở nơi khác chờ quyết định không. */
    AuthResponses.SessionStatus sessionStatus(CurrentUser current);

    /** AUTH-28: phiên đang dùng chọn "Tiếp tục dùng ở đây" ⇒ hủy lượt chờ ở trình duyệt kia. */
    void rejectTakeover(CurrentUser current, String takeoverId);

    /** Dọn lượt chờ hết hạn và hủy phiên Keycloak chưa dùng của chúng. */
    void expireTakeovers();

    RefreshResult refresh(String cookie);

    void logout(String cookie);

    AuthResponses.OtpRequest forgot(AuthRequests.Forgot request);

    AuthResponses.OtpRequest otpMetadata(String requestId);

    AuthResponses.OtpRequest resend(String requestId);

    AuthResponses.ResetToken verifyOtp(AuthRequests.VerifyOtp requestIdAndOtp, String requestId);

    void setPassword(AuthRequests.Reset request);

    void changePassword(CurrentUser current, AuthRequests.ChangePassword request);
}
