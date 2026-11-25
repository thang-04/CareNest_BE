package com.carenest.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.json.JsonMapper;

class RestAuthenticationEntryPointTest {
    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(new JsonMapper());

    @Test
    @DisplayName("AUTH-28: token của phiên bị đăng nhập nơi khác thay thế trả 401 mã 1016")
    void replacedSessionGetsItsOwnCode() throws Exception {
        var invalid =
                new JwtValidationException("replaced", List.of(new OAuth2Error(WebSessionRevocations.ERROR_CODE)));
        var response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InvalidBearerTokenException("x", invalid));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("1016");
    }

    @Test
    @DisplayName("Token của phiên đã đăng xuất trả 401/1002 thường, không báo đăng nhập nơi khác")
    void signedOutSessionGetsTheGenericCode() throws Exception {
        var invalid =
                new JwtValidationException("ended", List.of(new OAuth2Error(WebSessionRevocations.ENDED_ERROR_CODE)));
        var response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InvalidBearerTokenException("x", invalid));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("1002").doesNotContain("1016");
    }

    @Test
    void otherInvalidTokensKeepTheGenericCode() throws Exception {
        var invalid = new JwtValidationException("expired", List.of(new OAuth2Error("invalid_token")));
        var response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InvalidBearerTokenException("x", invalid));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("1002").doesNotContain("1016");
    }
}
