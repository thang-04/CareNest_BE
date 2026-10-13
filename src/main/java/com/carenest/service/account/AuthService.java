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

    RefreshResult refresh(String cookie);

    void logout(String cookie);

    AuthResponses.OtpRequest forgot(AuthRequests.Forgot request);

    AuthResponses.OtpRequest otpMetadata(String requestId);

    AuthResponses.OtpRequest resend(String requestId);

    AuthResponses.ResetToken verifyOtp(AuthRequests.VerifyOtp requestIdAndOtp, String requestId);

    void setPassword(AuthRequests.Reset request);

    void changePassword(CurrentUser current, AuthRequests.ChangePassword request);
}
