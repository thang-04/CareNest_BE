package com.carenest.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("carenest.auth")
public record AuthProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String encryptionKey,
        @DefaultValue("") String assertionSecret,
        @DefaultValue("carenest-auth-gateway") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("true") boolean secureCookies,
        @DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    @Override
    public String toString() {
        return "AuthProperties[enabled=" + enabled + ", secureCookies=" + secureCookies + "]";
    }
}
