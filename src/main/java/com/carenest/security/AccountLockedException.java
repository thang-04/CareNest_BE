package com.carenest.security;

import org.springframework.security.oauth2.jwt.BadJwtException;

/**
 * Thrown while decoding an access token whose user is locked or no longer exists.
 * Lets the error handler answer ACCOUNT_DISABLED instead of INVALID_TOKEN.
 */
public class AccountLockedException extends BadJwtException {

    public AccountLockedException() {
        super("Account is locked");
    }
}
