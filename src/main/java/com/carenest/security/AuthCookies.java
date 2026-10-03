package com.carenest.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * HttpOnly cookies that carry the tokens for web clients (JavaScript cannot read them).
 * - access_token: sent to every API (Path=/).
 * - refresh_token: sent only to auth endpoints (Path={api prefix}/auth).
 * CSRF protection relies on SameSite: the browser does not attach these cookies to cross-site POSTs.
 */
@Component
public class AuthCookies {

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";
    private static final String ACCESS_PATH = "/";

    private final Duration accessMaxAge;
    private final Duration refreshMaxAge;
    private final boolean secure;
    private final String sameSite;
    private final String refreshPath;

    public AuthCookies(@Value("${jwt.access-token-expiration}") Duration accessMaxAge,
                       @Value("${jwt.refresh-token-expiration}") Duration refreshMaxAge,
                       @Value("${app.auth.cookie.secure}") boolean secure,
                       @Value("${app.auth.cookie.same-site}") String sameSite,
                       @Value("${carenest.api.prefix}") String apiPrefix) {
        this.accessMaxAge = accessMaxAge;
        this.refreshMaxAge = refreshMaxAge;
        this.secure = secure;
        this.sameSite = sameSite;
        this.refreshPath = apiPrefix + "/auth";
    }

    public String accessToken(String token) {
        return build(ACCESS_TOKEN, token, ACCESS_PATH, accessMaxAge);
    }

    public String refreshToken(String token) {
        return build(REFRESH_TOKEN, token, refreshPath, refreshMaxAge);
    }

    public String clearAccessToken() {
        return build(ACCESS_TOKEN, "", ACCESS_PATH, Duration.ZERO);
    }

    public String clearRefreshToken() {
        return build(REFRESH_TOKEN, "", refreshPath, Duration.ZERO);
    }

    private String build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
