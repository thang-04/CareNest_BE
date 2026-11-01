package com.carenest.integration.keycloak;

import com.carenest.utils.enums.IdentityProviderErrorReason;
import lombok.Getter;

@Getter
public class IdentityProviderException extends RuntimeException {

    private final IdentityProviderErrorReason reason;

    public IdentityProviderException(IdentityProviderErrorReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }
}
