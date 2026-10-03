package com.carenest.controller;

import com.carenest.dto.request.*;
import com.carenest.dto.response.ApiResponse;
import com.carenest.dto.response.SignInResponse;
import com.carenest.security.AuthCookies;
import com.carenest.security.CookieOrHeaderBearerTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import com.carenest.service.AuthenticationService;
import com.carenest.service.ForgotPasswordService;
import com.carenest.utils.ChangePassword;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Web clients: access and refresh tokens travel in HttpOnly cookies, never in the JSON body.
 * Mobile app: sends header "X-Client-Type: mobile", gets both tokens in the body,
 * calls APIs with "Authorization: Bearer ..." and sends the refresh token in the body of refresh-token / logout.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    private static final String CLIENT_MOBILE = "mobile";

    private final AuthenticationService authenticationService;
    private final ForgotPasswordService forgotPasswordService;
    private final AuthCookies authCookies;
    private final BearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<SignInResponse>> login(
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType,
            @Valid @RequestBody SignInRequest request) {
        return tokenResponse("Đăng nhập thành công", authenticationService.login(request), clientType);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<SignInResponse>> refreshToken(
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType,
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String cookieToken) {
        SignInResponse tokens = authenticationService.refreshToken(resolveRefreshToken(request, cookieToken));
        return tokenResponse("Làm mới token thành công", tokens, clientType);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest,
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String cookieToken) {
        String accessToken = headerTokenResolver.resolve(httpRequest);
        if (accessToken == null) {
            accessToken = CookieOrHeaderBearerTokenResolver.cookieValue(httpRequest, AuthCookies.ACCESS_TOKEN);
        }
        authenticationService.logout(accessToken, resolveRefreshToken(request, cookieToken));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.clearAccessToken())
                .header(HttpHeaders.SET_COOKIE, authCookies.clearRefreshToken())
                .body(ApiResponse.success("Đăng xuất thành công"));
    }

    /**
     * Change password with the old password. Used by accounts without email
     * (parents on first login with the default password).
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changePassword(Long.valueOf(jwt.getSubject()), request);
        return ApiResponse.success("Đổi mật khẩu thành công");
    }

    // ---- Forgot password: forgot-password -> verify-otp -> reset-password ----
    // Identifier is an email (OTP by email) or a phone number (OTP by SMS).

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody IdentifierRequest request) {
        boolean byEmail = forgotPasswordService.sendOtp(request.identifier());
        return ApiResponse.success(byEmail
                ? "Mã OTP đã được gửi tới email của bạn"
                : "Mã OTP đã được gửi tới số điện thoại của bạn");
    }

    @PostMapping("/verify-otp")
    public ApiResponse<Void> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        forgotPasswordService.verifyOtp(request.otp(), request.identifier());
        return ApiResponse.success("Xác minh OTP thành công");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        forgotPasswordService.changePassword(request.identifier(),
                new ChangePassword(request.password(), request.confirmPassword()));
        return ApiResponse.success("Đổi mật khẩu thành công");
    }

    private ResponseEntity<ApiResponse<SignInResponse>> tokenResponse(String message, SignInResponse tokens,
                                                                      String clientType) {
        if (CLIENT_MOBILE.equalsIgnoreCase(clientType)) {
            return ResponseEntity.ok(ApiResponse.success(message, tokens));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.accessToken(tokens.accessToken()))
                .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(tokens.refreshToken()))
                .body(ApiResponse.success(message, tokens.withoutTokens()));
    }

    private static String resolveRefreshToken(RefreshTokenRequest request, String cookieToken) {
        if (request != null && StringUtils.hasText(request.refreshToken())) {
            return request.refreshToken();
        }
        return cookieToken;
    }
}
