package com.carenest.dto.request;

/**
 * Body for refresh-token and logout. Mobile app sends the refresh token here;
 * web sends nothing because the token is in the HttpOnly cookie.
 */
public record RefreshTokenRequest(String refreshToken) {
}
