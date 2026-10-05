package com.carenest.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.controller.SecurityTestController;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kiểm filter chain thật (Bearer token → JwtDecoder → role converter → @PreAuthorize).
 * JwtDecoder được mock; validator thật kiểm riêng ở JwtValidatorTest.
 */
@WebMvcTest(SecurityTestController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SecurityConfigTest {

    private static final String TEACHER_ENDPOINT = "/api/security-test/teacher";
    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

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
                .andExpect(jsonPath("$.desc").value("Unauthorized"));
    }

    @Test
    void shouldRequireAuthenticationForUnknownPathByDefault() throws Exception {
        mockMvc.perform(get("/api/not-declared-anywhere"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotRequireTokenForPublicHealthPath() throws Exception {
        // Actuator không nạp trong WebMvcTest nên trả 404, nhưng không bị chặn 401
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
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
        given(jwtDecoder.decode("parent-token")).willReturn(jwtWithRealmRoles("parent-token", "PARENT"));

        mockMvc.perform(get(TEACHER_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer parent-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void shouldAllowCorsPreflightFromConfiguredOrigin() throws Exception {
        mockMvc.perform(options(TEACHER_ENDPOINT)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    void shouldRejectCorsPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options(TEACHER_ENDPOINT)
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    private static Jwt jwtWithRealmRoles(String tokenValue, String... roles) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("kc-user-1")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
    }
}
