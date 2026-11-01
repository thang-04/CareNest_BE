package com.carenest.keycloak;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

final class ProviderErrors {
    private ProviderErrors() {}

    static BadRequestException bad(String code, String description) {
        return new BadRequestException(Response.status(400)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("error", code, "error_description", description))
                .build());
    }
}
