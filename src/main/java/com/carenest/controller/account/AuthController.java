package com.carenest.controller.account;

import com.carenest.config.AuthProperties;
import com.carenest.dto.account.AuthRequests;
import com.carenest.dto.account.AuthResponses;
import com.carenest.exception.GlobalException;
import com.carenest.service.account.AuthService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.Auth;
import com.carenest.utils.ResponseJson;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@PreAuthorize("permitAll()")
@SecurityRequirements
@Slf4j
public class AuthController {
    private static final String LOG_PREFIX = "[AuthController]";

    private final AuthService service;
    private final AuthProperties properties;
    private final String cookiePath;

    public AuthController(
            AuthService service, AuthProperties properties, @Value("${carenest.api.prefix}") String apiPrefix) {
        this.service = service;
        this.properties = properties;
        this.cookiePath = apiPrefix + "/auth";
    }

    @GetMapping("/csrf")
    public ResponseEntity<ResponseJson<AuthResponses.Csrf>> csrf(@Parameter(hidden = true) CsrfToken csrf) {
        String logPrefix = LOG_PREFIX + "|csrf";
        log.info("{}|START", logPrefix);
        AuthResponses.Csrf issued = service.csrf(csrf == null ? null : csrf.getToken());
        log.info("{}|END", logPrefix);
        return response(ApiCode.SUCCESSFUL, "CSRF token", issued);
    }

    @PostMapping("/login")
    @ApiResponse(responseCode = "428", description = "Verified temporary password requires first-login setup")
    @ApiResponse(responseCode = "200", description = "Login successful")
    public ResponseEntity<ResponseJson<AuthResponses.LoginData>> login(
            @Valid @RequestBody AuthRequests.Login request, HttpServletResponse servletResponse) {
        String logPrefix = LOG_PREFIX + "|login";
        log.info("{}|START", logPrefix);
        var result = service.login(request);
        if (result.data().passwordChangeRequired()) {
            log.info("{}|passwordChangeRequired|END", logPrefix);
            return response(
                    ApiCode.PASSWORD_CHANGE_REQUIRED, ApiCode.PASSWORD_CHANGE_REQUIRED.getDefaultDesc(), result.data());
        }
        setSessionCookie(
                servletResponse, result.cookie().value(), result.cookie().maxAgeSeconds());
        log.info("{}|END", logPrefix);
        return response(ApiCode.SUCCESSFUL, "Login successful", result.data());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ResponseJson<AuthResponses.AccessToken>> refresh(
            @CookieValue(name = Auth.SESSION_COOKIE, required = false) String cookie,
            @Valid @RequestBody(required = false) AuthRequests.Refresh request,
            HttpServletResponse servletResponse) {
        String logPrefix = LOG_PREFIX + "|refresh";
        log.info("{}|START", logPrefix);
        AuthService.RefreshResult result;
        try {
            result = service.refresh(cookie);
        } catch (GlobalException ex) {
            // Phiên hỏng/hết hạn thì xóa cookie; 503 giữ cookie để client retry.
            if (ex.getApiCode().getHttpStatus() == HttpStatus.UNAUTHORIZED) setSessionCookie(servletResponse, "", 0);
            throw ex;
        }
        setSessionCookie(
                servletResponse, result.cookie().value(), result.cookie().maxAgeSeconds());
        log.info("{}|END", logPrefix);
        return response(ApiCode.SUCCESSFUL, "Session refreshed", result.data());
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseJson<Void>> logout(
            @CookieValue(name = Auth.SESSION_COOKIE, required = false) String cookie,
            HttpServletResponse servletResponse) {
        String logPrefix = LOG_PREFIX + "|logout";
        log.info("{}|START", logPrefix);
        try {
            service.logout(cookie);
        } finally {
            // Luôn xóa cookie phiên kể cả khi revoke ở Keycloak lỗi
            setSessionCookie(servletResponse, "", 0);
        }
        log.info("{}|END", logPrefix);
        return response(ApiCode.SUCCESSFUL, "Logged out", null);
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<ResponseJson<AuthResponses.OtpRequest>> forgot(
            @Valid @RequestBody AuthRequests.Forgot request) {
        String logPrefix = LOG_PREFIX + "|forgot";
        log.info("{}|START", logPrefix);
        AuthResponses.OtpRequest accepted = service.forgot(request);
        log.info("{}|END", logPrefix);
        return response(ApiCode.SUCCESSFUL, "If the account is eligible, a recovery code has been sent", accepted);
    }

    @GetMapping("/password/otp/{requestId}")
    public ResponseEntity<ResponseJson<AuthResponses.OtpRequest>> otpMetadata(@PathVariable String requestId) {
        String logPrefix = LOG_PREFIX + "|otpMetadata";
        log.info("{}|START", logPrefix);
        AuthResponses.OtpRequest metadata = service.otpMetadata(requestId);
        log.info("{}|END", logPrefix);
        return response(ApiCode.SUCCESSFUL, "Recovery request", metadata);
    }

    @PostMapping("/password/otp/{requestId}/resend")
    public ResponseEntity<ResponseJson<AuthResponses.OtpRequest>> resend(@PathVariable String requestId) {
        String logPrefix = LOG_PREFIX + "|resend";
        log.info("{}|START", logPrefix);
        AuthResponses.OtpRequest accepted = service.resend(requestId);
        log.info("{}|END", logPrefix);
        return response(ApiCode.SUCCESSFUL, "If the account is eligible, a recovery code has been sent", accepted);
    }

    @PostMapping("/password/otp/{requestId}/verify")
    public ResponseEntity<ResponseJson<AuthResponses.ResetToken>> verifyOtp(
            @PathVariable String requestId, @Valid @RequestBody AuthRequests.VerifyOtp request) {
        String logPrefix = LOG_PREFIX + "|verifyOtp";
        log.info("{}|START", logPrefix);
        AuthResponses.ResetToken verified = service.verifyOtp(request, requestId);
        log.info("{}|END", logPrefix);
        return response(ApiCode.SUCCESSFUL, "Recovery code verified", verified);
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ResponseJson<Void>> setPassword(@Valid @RequestBody AuthRequests.Reset request) {
        String logPrefix = LOG_PREFIX + "|setPassword";
        log.info("{}|START", logPrefix);
        service.setPassword(request);
        log.info("{}|END", logPrefix);
        return response(ApiCode.SUCCESSFUL, "Password updated; sign in again", null);
    }

    private void setSessionCookie(HttpServletResponse servletResponse, String value, long maxAge) {
        servletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(Auth.SESSION_COOKIE, value)
                        .httpOnly(true)
                        .secure(properties.secureCookies())
                        .sameSite("Lax")
                        .path(cookiePath)
                        .maxAge(maxAge)
                        .build()
                        .toString());
    }

    private static <T> ResponseEntity<ResponseJson<T>> response(ApiCode code, String description, T data) {
        return ResponseEntity.status(code.getHttpStatus())
                .cacheControl(CacheControl.noStore())
                .body(ResponseJson.of(code, description, data));
    }
}
