package com.carenest.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

/**
 * Finds the access token: "Authorization: Bearer ..." header first (mobile app),
 * then the access_token cookie (web).
 * Public endpoints are skipped, so an expired cookie never blocks login or refresh.
 */
public class CookieOrHeaderBearerTokenResolver implements BearerTokenResolver {

    private final BearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    private final Set<String> publicPaths;

    public CookieOrHeaderBearerTokenResolver(String... publicPaths) {
        this.publicPaths = Set.of(publicPaths);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (publicPaths.contains(path)) {
            return null;
        }

        String headerToken = headerResolver.resolve(request);
        if (headerToken != null) {
            return headerToken;
        }
        return cookieValue(request, AuthCookies.ACCESS_TOKEN);
    }

    public static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }
}
