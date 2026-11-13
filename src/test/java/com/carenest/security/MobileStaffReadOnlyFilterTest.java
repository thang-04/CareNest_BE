package com.carenest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

class MobileStaffReadOnlyFilterTest {

    private static final ClientPlatformProperties PLATFORMS = new ClientPlatformProperties(
            List.of("carenest-web"),
            List.of("carenest-mobile"),
            List.of(),
            List.of("GET /children/{childId:\\d+}"),
            List.of(),
            List.of());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("AUTH-21: không có transaction manager thì từ chối BGH xem trên App thay vì cho qua")
    void shouldFailClosedWithoutTransactionManager() throws Exception {
        // Spring Security 7 gắn thêm FACTOR_BEARER cho token Bearer: không được coi là role khác
        authenticate("ROLE_PRINCIPAL", "FACTOR_BEARER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new MobileStaffReadOnlyFilter(PLATFORMS, "/api", null, JsonMapper.builder().build())
                .doFilter(new MockHttpServletRequest("GET", "/api/children/12"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("1302");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("AUTH-21: mẫu có ràng buộc số không khớp path chữ cùng cấp")
    void shouldMatchOnlyNumericChildId() {
        var matcher = MobileStaffReadOnlyFilter.matcher("/api", PLATFORMS.mobileStaffReadPaths());

        assertThat(matcher.matches(new MockHttpServletRequest("GET", "/api/children/12")))
                .isTrue();
        assertThat(matcher.matches(new MockHttpServletRequest("GET", "/api/children/duplicate-check")))
                .isFalse();
        assertThat(matcher.matches(new MockHttpServletRequest("POST", "/api/children/12")))
                .isFalse();
    }

    @Test
    void shouldRejectMalformedEntryAtStartup() {
        assertThatThrownBy(() -> MobileStaffReadOnlyFilter.matcher("/api", List.of("/children")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDetectReadOnlySqlStateInCauseChain() {
        var error = new DataAccessResourceFailureException(
                "write", new SQLException("cannot execute INSERT in a read-only transaction", "25006"));

        assertThat(MobileStaffReadOnlyFilter.isReadOnlyViolation(error)).isTrue();
        assertThat(MobileStaffReadOnlyFilter.isReadOnlyViolation(new IllegalStateException("x")))
                .isFalse();
    }

    private static void authenticate(String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("kc-user-1")
                .claim("azp", "carenest-mobile")
                .claim("realm_access", Map.of("roles", List.of()))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt,
                        List.of(authorities).stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList()));
    }
}
