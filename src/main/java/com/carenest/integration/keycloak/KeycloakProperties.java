package com.carenest.integration.keycloak;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("carenest.keycloak")
public record KeycloakProperties(
        String baseUrl,
        String realm,
        String adminClientId,
        String adminClientSecret,
        boolean actionsEmailEnabled,
        Duration connectTimeout,
        Duration readTimeout
) {

    // Không để secret lọt vào log khi record bị in ra
    @Override
    public String toString() {
        return "KeycloakProperties[baseUrl=" + baseUrl + ", realm=" + realm + ", adminClientId=" + adminClientId
                + ", actionsEmailEnabled=" + actionsEmailEnabled + "]";
    }
}
