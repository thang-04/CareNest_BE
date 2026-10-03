package com.carenest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignInResponse(
        // Tokens are only in the body for the mobile app; web gets them as HttpOnly cookies.
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        // FE must redirect to the change-password screen when this is true.
        boolean mustChangePassword
) {
    public static SignInResponse bearer(String accessToken, String refreshToken,
                                        long expiresInSeconds, boolean mustChangePassword) {
        return new SignInResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, mustChangePassword);
    }

    public SignInResponse withoutTokens() {
        return new SignInResponse(null, null, null, expiresIn, mustChangePassword);
    }
}
