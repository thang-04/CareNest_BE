package com.carenest.integration.keycloak;

import com.carenest.config.AuthProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Adapter Keycloak cho AuthGatewayClient: client confidential; chỉ server giữ client secret và passwords chỉ đi qua HTTPS riêng. */
@Slf4j
@Component
public final class KeycloakAuthGatewayClient implements AuthGatewayClient {
    private final AuthProperties web;
    private final KeycloakProperties admin;
    private final RestClient client;
    private final String realmUrl;
    private String cachedAdminToken;
    private Instant adminTokenExpiresAt = Instant.EPOCH;

    @Autowired
    public KeycloakAuthGatewayClient(AuthProperties web, KeycloakProperties admin) {
        this(web, admin, RestClient.builder().requestFactory(requestFactory(admin)));
    }

    KeycloakAuthGatewayClient(AuthProperties web, KeycloakProperties admin, RestClient.Builder builder) {
        this.web = web;
        this.admin = admin;
        String base = admin.baseUrl().replaceAll("/+$", "");
        this.realmUrl = base + "/realms/" + admin.realm();
        this.client = builder.baseUrl(base).build();
    }

    private static org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory(
            KeycloakProperties admin) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(admin.connectTimeout());
        factory.setReadTimeout(admin.readTimeout());
        return factory;
    }

    @Override
    public Tokens login(String identifier, String password) {
        var form = form("password");
        form.add("username", identifier);
        form.add("password", password);
        return exchange(form);
    }

    @Override
    public Tokens refresh(String refreshToken) {
        var form = form("refresh_token");
        form.add("refresh_token", refreshToken);
        return exchange(form);
    }

    @Override
    public void logout(String refreshToken) {
        var form = form("logout");
        form.add("refresh_token", refreshToken);
        try {
            client.post()
                    .uri(realmUrl + "/protocol/openid-connect/logout")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            // Best-effort: caller vẫn xóa cookie phiên; không log token.
            log.warn("[authGatewayLogout]|keycloakUnavailable");
        }
    }

    @Override
    public String verifiedCapability(String assertion) {
        Map<String, Object> reply = adminPost("password/capability", Map.of("assertion", assertion));
        Object token = reply.get("token");
        if (!(token instanceof String value) || !value.matches("[A-Za-z0-9_-]{43}")) throw new ProviderUnavailable();
        return value;
    }

    @Override
    public void changePassword(String capability, String newPassword, String confirmation) {
        providerPost(
                "password/setup",
                Map.of("token", capability, "newPassword", newPassword, "confirmPassword", confirmation));
    }

    @Override
    public void sendOtp(String keycloakUserId, String code) {
        providerPost("recovery/email", Map.of("userId", keycloakUserId, "code", code));
    }

    @Override
    public void sendReactSetupEmail(String keycloakUserId) {
        providerPost("setup/email", Map.of("userId", keycloakUserId));
    }

    private Map<String, Object> adminPost(String path, Map<String, String> body) {
        try {
            Map<String, Object> reply = client.post()
                    .uri(realmUrl + "/carenest-auth/" + path)
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (reply == null) throw new ProviderUnavailable();
            return reply;
        } catch (ProviderFailure ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw providerFailure(ex);
        } catch (RestClientException ex) {
            log.warn(
                    "[authGatewayProvider]|operation={}|keycloakUnreachable|type={}",
                    path,
                    ex.getClass().getSimpleName());
            throw new ProviderUnavailable();
        }
    }

    private void providerPost(String path, Map<String, String> body) {
        try {
            client.post()
                    .uri(realmUrl + "/carenest-auth/" + path)
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (ProviderFailure ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw providerFailure(ex);
        } catch (RestClientException ex) {
            log.warn(
                    "[authGatewayProvider]|operation={}|keycloakUnreachable|type={}",
                    path,
                    ex.getClass().getSimpleName());
            throw new ProviderUnavailable();
        }
    }

    private RuntimeException providerFailure(RestClientResponseException ex) {
        log.warn("[authGatewayProvider]|keycloakStatus={}", ex.getStatusCode().value());
        ErrorBody body = null;
        try {
            body = ex.getResponseBodyAs(ErrorBody.class);
        } catch (RuntimeException ignored) {
            /* response body is untrusted */
        }
        if (ex.getStatusCode().value() == 400 && body != null && "password_policy_rejected".equals(body.error()))
            return new PasswordRejected();
        if (ex.getStatusCode().value() == 400 && body != null && "capability_invalid".equals(body.error()))
            return new CapabilityRejected();
        return new ProviderUnavailable();
    }

    private synchronized String adminToken() {
        Instant now = Instant.now();
        if (cachedAdminToken != null && now.isBefore(adminTokenExpiresAt)) return cachedAdminToken;
        if (admin.adminClientSecret() == null || admin.adminClientSecret().length() < 32)
            throw new ProviderUnavailable();
        var credentials = new LinkedMultiValueMap<String, String>();
        credentials.add("grant_type", "client_credentials");
        credentials.add("client_id", admin.adminClientId());
        credentials.add("client_secret", admin.adminClientSecret());
        try {
            AdminToken token = client.post()
                    .uri(realmUrl + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(credentials)
                    .retrieve()
                    .body(AdminToken.class);
            if (token == null || token.accessToken() == null || token.expiresIn() <= 0) throw new ProviderUnavailable();
            cachedAdminToken = token.accessToken();
            adminTokenExpiresAt = now.plusSeconds(token.expiresIn()).minusSeconds(30);
            return cachedAdminToken;
        } catch (ProviderFailure ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ProviderUnavailable();
        }
    }

    private Tokens exchange(MultiValueMap<String, String> form) {
        try {
            Tokens response = client.post()
                    .uri(realmUrl + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Tokens.class);
            if (response == null
                    || response.accessToken() == null
                    || response.accessToken().isBlank()
                    || response.expiresIn() <= 0
                    || "password".equals(form.getFirst("grant_type"))
                            && (response.refreshToken() == null || response.refreshExpiresIn() <= 0)) {
                throw new ProviderUnavailable();
            }
            return response;
        } catch (FirstLoginRequired | InvalidCredentials | ProviderUnavailable ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            ErrorBody body = null;
            try {
                body = ex.getResponseBodyAs(ErrorBody.class);
            } catch (RuntimeException ignored) {
                /* untrusted IdP body is never returned/logged */
            }
            log.warn(
                    "[authGatewayToken]|keycloakStatus={}|oauthErrorCode={}",
                    ex.getStatusCode().value(),
                    body == null ? "unknown" : safeOAuthError(body.error()));
            if (ex.getStatusCode().value() == 400
                    && "password".equals(form.getFirst("grant_type"))
                    && body != null
                    && "carenest_password_change_required".equals(body.error())
                    && body.userId() != null
                    && body.userId().matches("[A-Za-z0-9_-]{1,36}")
                    && body.setupToken() != null
                    && body.setupToken().matches("[A-Za-z0-9_-]{43}")
                    && body.expiresIn() != null
                    && body.expiresIn() > 0
                    && body.expiresIn() <= 600) {
                throw new FirstLoginRequired(body.userId(), body.setupToken(), body.expiresIn());
            }
            if ("password".equals(form.getFirst("grant_type"))
                    && ex.getStatusCode().value() == 400
                    && body != null
                    && "invalid_grant".equals(body.error())) throw new InvalidCredentials();
            if ("refresh_token".equals(form.getFirst("grant_type"))
                    && ex.getStatusCode().value() == 400
                    && body != null
                    && "invalid_grant".equals(body.error())) throw new SessionRejected();
            throw new ProviderUnavailable();
        } catch (RestClientException ex) {
            log.warn(
                    "[authGatewayToken]|keycloakUnreachable|type={}",
                    ex.getClass().getSimpleName());
            throw new ProviderUnavailable();
        }
    }

    private MultiValueMap<String, String> form(String grant) {
        if (web.clientSecret() == null || web.clientSecret().length() < 32) throw new ProviderUnavailable();
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", grant);
        form.add("client_id", web.clientId());
        form.add("client_secret", web.clientSecret());
        return form;
    }

    private static String safeOAuthError(String code) {
        return code != null && code.matches("[a-z_]{1,48}") ? code : "unknown";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorBody(
            @JsonProperty("error") String error,
            @JsonProperty("user_id") String userId,
            @JsonProperty("setup_token") String setupToken,
            @JsonProperty("expires_in") Long expiresIn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdminToken(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {}
}
