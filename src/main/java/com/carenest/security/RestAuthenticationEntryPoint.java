package com.carenest.security;

import com.carenest.utils.ApiCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 401: thiếu token, token sai chữ ký, hết hạn, sai issuer/audience. 503: không kiểm được token vì chưa lấy được
 * JWKS từ Keycloak (Keycloak tắt khi API chưa cache key) — lỗi phía server, không phải token sai. Không log token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        if (authException instanceof AuthenticationServiceException) {
            log.error("[commence]|path={}|tokenVerificationUnavailable", request.getRequestURI(), authException);
            SecurityResponseWriter.write(
                    response,
                    objectMapper,
                    ApiCode.AUTH_SERVICE_UNAVAILABLE,
                    ApiCode.AUTH_SERVICE_UNAVAILABLE.getDefaultDesc());
            return;
        }
        log.warn(
                "[commence]|path={}|type={}",
                request.getRequestURI(),
                authException.getClass().getSimpleName());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        SecurityResponseWriter.write(
                response, objectMapper, ApiCode.UNAUTHENTICATED, ApiCode.UNAUTHENTICATED.getDefaultDesc());
    }
}
