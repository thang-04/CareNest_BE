package com.carenest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.controller.SecurityTestController;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.utils.ApiCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Kiểm filter chain thật (Bearer token → JwtDecoder → role converter → @PreAuthorize).
 * JwtDecoder được mock; validator thật kiểm riêng ở JwtValidatorTest.
 */
@WebMvcTest(SecurityTestController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(
        properties = {
            "carenest.security.platform.mobile-staff-read-paths="
                    + "GET /security-test/staff,GET /security-test/staff-write-attempt,"
                    + "GET /security-test/staff-system-write",
            "carenest.security.platform.mobile-staff-write-exceptions=POST /security-test/notification-read",
            "carenest.security.platform.mobile-staff-system-write-paths=GET /security-test/staff-system-write"
        })
class SecurityConfigTest {

    private static final String TEACHER_ENDPOINT = "/api/security-test/teacher";
    private static final String AUTHENTICATED_ENDPOINT = "/api/security-test/authenticated";
    private static final String STAFF_ENDPOINT = "/api/security-test/staff";
    private static final String WEB_CLIENT = "carenest-auth-gateway";
    private static final String MOBILE_CLIENT = "carenest-mobile";
    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get(TEACHER_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldRejectInvalidOrExpiredToken() throws Exception {
        given(jwtDecoder.decode("expired-token")).willThrow(new BadJwtException("Jwt expired"));

        mockMvc.perform(get(TEACHER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.desc").value(ApiCode.UNAUTHENTICATED.getDefaultDesc()));
    }

    @Test
    void shouldRequireAuthenticationForUnknownPathByDefault() throws Exception {
        mockMvc.perform(get("/api/not-declared-anywhere")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotRequireTokenForPublicHealthPath() throws Exception {
        // Actuator không nạp trong WebMvcTest nên trả 404, nhưng không bị chặn 401
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowTeacherRoleFromKeycloakRealmAccess() throws Exception {
        given(jwtDecoder.decode("teacher-token")).willReturn(jwtWithRealmRoles("teacher-token", "TEACHER"));

        mockMvc.perform(get(TEACHER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer teacher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldForbidParentOnTeacherEndpoint() throws Exception {
        given(jwtDecoder.decode("parent-token")).willReturn(clientJwt("parent-token", MOBILE_CLIENT, "PARENT"));

        mockMvc.perform(get(TEACHER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer parent-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("AUTH-21: token Web của tài khoản chỉ có PARENT bị từ chối mọi API")
    void shouldForbidParentOnlyWebTokenOnEveryEndpoint() throws Exception {
        given(jwtDecoder.decode("web-parent-token")).willReturn(clientJwt("web-parent-token", WEB_CLIENT, "PARENT"));

        mockMvc.perform(get(AUTHENTICATED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer web-parent-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.desc").value(ApiCode.FORBIDDEN.getDefaultDesc()));
    }

    @Test
    @DisplayName("AUTH-21: GV kiêm phụ huynh dùng Web với quyền GV")
    void shouldAllowTeacherAndParentWebTokenAsTeacher() throws Exception {
        given(jwtDecoder.decode("web-teacher-parent"))
                .willReturn(clientJwt("web-teacher-parent", WEB_CLIENT, "TEACHER", "PARENT"));

        mockMvc.perform(get(TEACHER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer web-teacher-parent"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-21: BGH thuần trên App gọi GET ngoài danh sách cho phép ⇒ 1301")
    void shouldRejectStaffReadOutsideAllowListOnMobile() throws Exception {
        givenToken("mobile-principal", MOBILE_CLIENT, "PRINCIPAL");

        mockMvc.perform(get(AUTHENTICATED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer mobile-principal"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(1301));
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    @DisplayName("AUTH-21: BGH xem được trên App, trong transaction chỉ đọc")
    void shouldAllowStaffReadOnMobileInReadOnlyTransaction() throws Exception {
        givenToken("mobile-principal", MOBILE_CLIENT, "PRINCIPAL");
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());

        mockMvc.perform(get(STAFF_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer mobile-principal"))
                .andExpect(status().isOk());
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isTrue();
    }

    @Test
    @DisplayName("AUTH-21: BGH thuần trên App không ghi được, kể cả thao tác của chính mình ⇒ 1301")
    void shouldRejectStaffWriteOnMobile() throws Exception {
        givenToken("mobile-vp", MOBILE_CLIENT, "VICE_PRINCIPAL");

        mockMvc.perform(post(STAFF_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer mobile-vp"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(1301));
        mockMvc.perform(post(AUTHENTICATED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer mobile-vp"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(1301));
    }

    @Test
    @DisplayName("AUTH-21: GET cố ghi DB khi BGH xem trên App ⇒ 1302")
    void shouldMapReadOnlyViolationForStaffOnMobile() throws Exception {
        givenToken("mobile-principal", MOBILE_CLIENT, "PRINCIPAL");
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());

        mockMvc.perform(get(STAFF_ENDPOINT + "-write-attempt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer mobile-principal"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(1302));
    }

    @Test
    @DisplayName("AUTH-21: lỗi chỉ đọc ngoài luồng BGH trên App vẫn là lỗi hệ thống")
    void shouldKeepReadOnlyViolationAsInternalErrorOnWeb() throws Exception {
        givenToken("web-principal", WEB_CLIENT, "PRINCIPAL");

        mockMvc.perform(get(STAFF_ENDPOINT + "-write-attempt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer web-principal"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("AUTH-21: GET lập dữ liệu hệ thống (sổ suất) không bọc chỉ đọc")
    void shouldSkipReadOnlyTransactionForSystemWritePath() throws Exception {
        givenToken("mobile-principal", MOBILE_CLIENT, "PRINCIPAL");

        mockMvc.perform(get(STAFF_ENDPOINT + "-system-write")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer mobile-principal"))
                .andExpect(status().isOk());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    @DisplayName("AUTH-21: BGH trên App được đánh dấu thông báo đã đọc, đăng ký thiết bị")
    void shouldAllowNotificationWriteExceptionForStaffOnMobile() throws Exception {
        givenToken("mobile-principal", MOBILE_CLIENT, "PRINCIPAL");

        mockMvc.perform(post("/api/security-test/notification-read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer mobile-principal"))
                .andExpect(status().isOk());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    @DisplayName("AUTH-21: PHT kiêm GV trên App vẫn ghi được việc của GV")
    void shouldKeepTeacherWriteForMultiRoleStaffOnMobile() throws Exception {
        givenToken("mobile-vp-teacher", MOBILE_CLIENT, "VICE_PRINCIPAL", "TEACHER");

        mockMvc.perform(post(TEACHER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer mobile-vp-teacher"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUTH-21: PHT kiêm GV trên App không ghi được việc cần quyền BGH ⇒ 1101")
    void shouldDropStaffRoleOnWriteForMultiRoleOnMobile() throws Exception {
        givenToken("mobile-vp-teacher", MOBILE_CLIENT, "VICE_PRINCIPAL", "TEACHER");

        mockMvc.perform(post(STAFF_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer mobile-vp-teacher"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(1101));
    }

    @Test
    @DisplayName("AUTH-21: PHT kiêm GV trên App vẫn xem dữ liệu BGH, không bọc chỉ đọc")
    void shouldKeepStaffReadForMultiRoleOnMobile() throws Exception {
        givenToken("mobile-vp-teacher", MOBILE_CLIENT, "VICE_PRINCIPAL", "TEACHER");

        mockMvc.perform(get(STAFF_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer mobile-vp-teacher"))
                .andExpect(status().isOk());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    @DisplayName("AUTH-21: BGH trên Web ghi như cũ")
    void shouldKeepStaffWriteOnWeb() throws Exception {
        givenToken("web-principal", WEB_CLIENT, "PRINCIPAL");

        mockMvc.perform(post(STAFF_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer web-principal"))
                .andExpect(status().isOk());
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    @DisplayName("AUTH-21: ADMIN không dùng App ⇒ 1101")
    void shouldForbidAdminOnMobileToken() throws Exception {
        givenToken("mobile-admin", MOBILE_CLIENT, "ADMIN");

        mockMvc.perform(get(AUTHENTICATED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer mobile-admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(1101));
    }

    private void givenToken(String tokenValue, String clientId, String... roles) {
        given(jwtDecoder.decode(tokenValue)).willReturn(clientJwt(tokenValue, clientId, roles));
    }

    @Test
    @DisplayName("AUTH-21: token của client không khai báo bị từ chối")
    void shouldForbidTokenFromUnknownClient() throws Exception {
        given(jwtDecoder.decode("unknown-client")).willReturn(clientJwt("unknown-client", "other-app", "TEACHER"));

        mockMvc.perform(get(AUTHENTICATED_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer unknown-client"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowCorsPreflightFromConfiguredOrigin() throws Exception {
        mockMvc.perform(options(TEACHER_ENDPOINT)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization, X-Edit-Session, X-Plan-Revision, Idempotency-Key, Range"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("X-Edit-Session")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Idempotency-Key")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Range")));
    }

    @Test
    void shouldAllowClassDocVersionHeadersInPreflight() throws Exception {
        mockMvc.perform(options(TEACHER_ENDPOINT)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization, content-type, x-class-doc-version, x-dossier-version"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("x-class-doc-version")))
                .andExpect(
                        header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("x-dossier-version")));
    }

    @Test
    void shouldRejectCorsPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options(TEACHER_ENDPOINT)
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private static Jwt jwtWithRealmRoles(String tokenValue, String... roles) {
        return clientJwt(tokenValue, WEB_CLIENT, roles);
    }

    private static Jwt clientJwt(String tokenValue, String clientId, String... roles) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("kc-user-1")
                .claim("azp", clientId)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
    }

    @Test
    void shouldReturn503WhenSigningKeysCannotBeFetched() throws Exception {
        // Keycloak tắt khi API chưa cache JWKS: lỗi phía server, không phải token sai
        given(jwtDecoder.decode("valid-token")).willThrow(new JwtException("Couldn't retrieve remote JWK set"));

        mockMvc.perform(get(TEACHER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.desc").value(ApiCode.AUTH_SERVICE_UNAVAILABLE.getDefaultDesc()));
    }

    @Test
    void shouldBlockInactiveAccountEvenOnEndpointWithoutCurrentUserParameter() throws Exception {
        given(jwtDecoder.decode("teacher-token")).willReturn(jwtWithRealmRoles("teacher-token", "TEACHER"));
        given(currentUserService.getCurrentUser()).willThrow(GlobalException.of(ApiCode.ACCOUNT_INACTIVE));

        mockMvc.perform(get(TEACHER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer teacher-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.desc").value(ApiCode.ACCOUNT_INACTIVE.getDefaultDesc()));
    }
}
