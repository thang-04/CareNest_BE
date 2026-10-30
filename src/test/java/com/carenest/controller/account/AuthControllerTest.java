package com.carenest.controller.account;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.account.AuthResponses;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.account.AuthService;
import com.carenest.utils.ApiCode;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(
        controllers = {AuthController.class, PasswordController.class},
        properties = {"carenest.auth.enabled=true", "carenest.auth.allowed-origins=https://app.example.test"})
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuthControllerTest {
    private static final String LOGIN = "{\"identifier\":\"demo\",\"password\":\"password\",\"remember\":true}";
    private static final String OPAQUE = "a".repeat(43);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService service;

    @MockitoBean
    private CurrentUserService currentUsers;

    @Test
    void csrfBootstrapReturnsReadableScopedCookieAndRawHeaderToken() throws Exception {
        given(service.csrf(any()))
                .willAnswer(invocation -> new AuthResponses.Csrf(invocation.getArgument(0), "X-XSRF-TOKEN"));
        var result = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().path("XSRF-TOKEN", "/api/auth"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().secure("XSRF-TOKEN", true))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn();
        String token = result.getResponse().getCookie("XSRF-TOKEN").getValue();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .contains(token);
    }

    @Test
    void missingAndMismatchedCsrfNeverReachLogin() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", "expected"))
                        .header("X-XSRF-TOKEN", "different")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void loginSetsOnlyOpaqueRememberedSessionCookie() throws Exception {
        given(service.login(any()))
                .willReturn(new AuthService.LoginResult(
                        new AuthResponses.LoginData(
                                "access-token", Instant.now().plusSeconds(300), false, null, null),
                        new AuthService.SessionCookie(OPAQUE, 36000)));
        mvc.perform(csrf(post("/api/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(cookie().value("CARENEST_SESSION", OPAQUE))
                .andExpect(cookie().path("CARENEST_SESSION", "/api/auth"))
                .andExpect(cookie().httpOnly("CARENEST_SESSION", true))
                .andExpect(cookie().secure("CARENEST_SESSION", true))
                .andExpect(cookie().maxAge("CARENEST_SESSION", 36000))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("access-token"))));
    }

    @Test
    void unrememberedLoginUsesBrowserSessionCookie() throws Exception {
        given(service.login(any()))
                .willReturn(new AuthService.LoginResult(
                        new AuthResponses.LoginData("access-token", Instant.now(), false, null, null),
                        new AuthService.SessionCookie(OPAQUE, -1)));
        mvc.perform(csrf(post("/api/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("CARENEST_SESSION", -1));
    }

    @Test
    void firstLoginReturns428WithoutSessionOrAccessToken() throws Exception {
        given(service.login(any()))
                .willReturn(new AuthService.LoginResult(
                        new AuthResponses.LoginData(null, Instant.now(), true, OPAQUE, null), null));
        mvc.perform(csrf(post("/api/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(428))
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.data.changeToken").value(OPAQUE))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(cookie().doesNotExist("CARENEST_SESSION"));
    }

    @Test
    @DisplayName("AUTH-07: refresh trả cookie mã hóa mới cùng thuộc tính bảo mật; logout xóa cookie")
    void refreshReadsOnlyScopedCookieAndLogoutClearsIt() throws Exception {
        given(service.refresh(OPAQUE))
                .willReturn(new AuthService.RefreshResult(
                        new AuthResponses.AccessToken("refreshed", Instant.now()),
                        new AuthService.SessionCookie("rotated-cookie", 35400)));
        mvc.perform(csrf(post("/api/auth/refresh")).cookie(new Cookie("CARENEST_SESSION", OPAQUE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("refreshed"))
                .andExpect(cookie().value("CARENEST_SESSION", "rotated-cookie"))
                .andExpect(cookie().path("CARENEST_SESSION", "/api/auth"))
                .andExpect(cookie().httpOnly("CARENEST_SESSION", true))
                .andExpect(cookie().maxAge("CARENEST_SESSION", 35400))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("refreshed"))));
        mvc.perform(csrf(post("/api/auth/logout")).cookie(new Cookie("CARENEST_SESSION", OPAQUE)))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("CARENEST_SESSION", 0));
        verify(service).logout(OPAQUE);
    }

    @Test
    @DisplayName("AUTH-07: refresh 401 xóa cookie, 503 giữ cookie để client retry")
    void refreshClearsCookieOnlyWhenSessionIsInvalid() throws Exception {
        given(service.refresh(OPAQUE))
                .willThrow(new GlobalException(ApiCode.UNAUTHORIZED, "Web session is invalid or expired"));
        mvc.perform(csrf(post("/api/auth/refresh")).cookie(new Cookie("CARENEST_SESSION", OPAQUE)))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("CARENEST_SESSION", 0));
        given(service.refresh("b".repeat(43)))
                .willThrow(new GlobalException(ApiCode.SERVICE_UNAVAILABLE, "Authentication service unavailable"));
        mvc.perform(csrf(post("/api/auth/refresh")).cookie(new Cookie("CARENEST_SESSION", "b".repeat(43))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(cookie().doesNotExist("CARENEST_SESSION"));
    }

    @Test
    @DisplayName("AUTH-21: refresh 403 (không còn role Web / inactive) xóa cookie vì token trong đó đã bị thu hồi")
    void refreshClearsCookieWhenForbidden() throws Exception {
        given(service.refresh(OPAQUE))
                .willThrow(new GlobalException(ApiCode.FORBIDDEN, "This account cannot sign in on the Web app"));
        mvc.perform(csrf(post("/api/auth/refresh")).cookie(new Cookie("CARENEST_SESSION", OPAQUE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.desc").value("This account cannot sign in on the Web app"))
                .andExpect(cookie().maxAge("CARENEST_SESSION", 0));
    }

    @Test
    void unknownAuthPathsAndWrongHttpMethodsStayProtected() throws Exception {
        mvc.perform(get("/api/auth/unknown")).andExpect(status().isUnauthorized());
        mvc.perform(csrf(post("/api/auth/unknown"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/password/otp/" + OPAQUE + "/extra")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPasswordMutationRequiresBearerButNoCsrf() throws Exception {
        given(currentUsers.getCurrentUser())
                .willReturn(new com.carenest.security.CurrentUser(
                        1L,
                        "kc-user",
                        "demo",
                        "Demo",
                        null,
                        java.util.Set.of(com.carenest.utils.enums.CareNestRole.TEACHER)));
        String body =
                "{\"currentPassword\":\"Old-pass1\",\"newPassword\":\"New-pass2\",\"confirmPassword\":\"New-pass2\"}";
        mvc.perform(put("/api/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/password")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(service).changePassword(any(), any());
    }

    @Test
    void onlyAuthCorsAllowsCredentialsForExactConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://app.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/me/password")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void recoveryEndpointsKeepEnvelopeAndAcceptNoBearer() throws Exception {
        var pending = new AuthResponses.OtpRequest(
                OPAQUE, Instant.now().plusSeconds(300), Instant.now().plusSeconds(60));
        given(service.forgot(any())).willReturn(pending);
        given(service.otpMetadata(OPAQUE)).willReturn(pending);
        given(service.resend(OPAQUE)).willReturn(pending);
        given(service.verifyOtp(any(), org.mockito.ArgumentMatchers.eq(OPAQUE)))
                .willReturn(new AuthResponses.ResetToken(OPAQUE, Instant.now().plusSeconds(600)));
        mvc.perform(csrf(post("/api/auth/password/forgot"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo@example.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(OPAQUE))
                .andExpect(jsonPath("$.data.otp").doesNotExist());
        mvc.perform(get("/api/auth/password/otp/" + OPAQUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(OPAQUE));
        mvc.perform(csrf(post("/api/auth/password/otp/" + OPAQUE + "/resend"))).andExpect(status().isOk());
        mvc.perform(csrf(post("/api/auth/password/otp/" + OPAQUE + "/verify"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resetToken").value(OPAQUE));
        mvc.perform(csrf(post("/api/auth/password/reset"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + OPAQUE
                                + "\",\"newPassword\":\"New-pass1\",\"confirmPassword\":\"New-pass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(service).setPassword(any());
    }

    @Test
    void everyPublicMutationRequiresCsrfEvenWithBearer() throws Exception {
        for (String path : java.util.List.of(
                "login",
                "refresh",
                "logout",
                "password/forgot",
                "password/reset",
                "password/otp/" + OPAQUE + "/resend",
                "password/otp/" + OPAQUE + "/verify")) {
            mvc.perform(post("/api/auth/" + path).header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void authOriginCanCallBearerBusinessApiWithoutCredentialedCors() throws Exception {
        mvc.perform(options("/api/accounts/me")
                        .header(HttpHeaders.ORIGIN, "https://app.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void invalidInputDoesNotReachService() throws Exception {
        mvc.perform(csrf(post("/api/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    private static MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", "test-csrf")).header("X-XSRF-TOKEN", "test-csrf");
    }
}
