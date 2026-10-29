package com.carenest.integration.keycloak;

import com.carenest.utils.enums.IdentityProviderErrorReason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adapter Keycloak Admin REST API, xác thực bằng service account của client {@code carenest-backend}
 * (client_credentials). Không log request/response body vì có thể chứa mật khẩu tạm.
 */
@Slf4j
@Component
public class KeycloakIdentityProviderClient implements IdentityProviderClient {

    private static final Duration TOKEN_EXPIRY_MARGIN = Duration.ofSeconds(30);
    private static final String UPDATE_PASSWORD =
            "UPDATE_PASSWORD"; // carenest:allow-secret: tên required action Keycloak

    private final KeycloakProperties properties;
    private final Clock clock;
    private final RestClient restClient;

    private String cachedAdminToken;
    private Instant adminTokenExpiresAt = Instant.EPOCH;

    @Autowired
    public KeycloakIdentityProviderClient(KeycloakProperties properties, Clock clock) {
        this(properties, clock, RestClient.builder().requestFactory(requestFactory(properties)));
    }

    // Dùng trong test để gắn MockRestServiceServer vào builder
    KeycloakIdentityProviderClient(KeycloakProperties properties, Clock clock, RestClient.Builder builder) {
        this.properties = properties;
        this.clock = clock;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public String createUser(NewIdentityUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", user.username());
        body.put("enabled", true);
        // Họ tên đầy đủ để ở firstName: tên tiếng Việt không tách họ/tên tin cậy; realm bỏ bắt buộc lastName
        body.put("firstName", user.fullName());
        if (user.email() != null) {
            body.put("email", user.email());
        }
        body.put("requiredActions", List.of(UPDATE_PASSWORD));
        if (user.temporaryPassword() != null) {
            body.put(
                    "credentials",
                    List.of(Map.of("type", "password", "value", user.temporaryPassword(), "temporary", true)));
        }

        ResponseEntity<Void> response = call(
                "createUser",
                () -> restClient
                        .post()
                        .uri("/admin/realms/{realm}/users", properties.realm())
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity());
        URI location = response.getHeaders().getLocation();
        if (location == null) {
            throw new IdentityProviderException(
                    IdentityProviderErrorReason.UNAVAILABLE, "Keycloak did not return created user id", null);
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public void assignRealmRole(String userId, String roleName) {
        // Lấy role qua danh sách "available" vì service account chỉ có manage-users, không có view-realm
        RoleRepresentation[] available = call(
                "assignRealmRole",
                () -> restClient
                        .get()
                        .uri(
                                "/admin/realms/{realm}/users/{id}/role-mappings/realm/available",
                                properties.realm(),
                                userId)
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .retrieve()
                        .body(RoleRepresentation[].class));
        RoleRepresentation role = available == null
                ? null
                : Arrays.stream(available)
                        .filter(candidate -> roleName.equals(candidate.name()))
                        .findFirst()
                        .orElse(null);
        if (role == null) {
            throw new IdentityProviderException(
                    IdentityProviderErrorReason.UNAVAILABLE, "Realm role not available: " + roleName, null);
        }
        call(
                "assignRealmRole",
                () -> restClient
                        .post()
                        .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), userId)
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(List.of(Map.of("id", role.id(), "name", role.name())))
                        .retrieve()
                        .toBodilessEntity());
    }

    @Override
    public List<String> getRealmRoleNames(String userId) {
        RoleRepresentation[] assigned = call(
                "getRealmRoleNames",
                () -> restClient
                        .get()
                        .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), userId)
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .retrieve()
                        .body(RoleRepresentation[].class));
        return assigned == null
                ? List.of()
                : Arrays.stream(assigned).map(RoleRepresentation::name).toList();
    }

    @Override
    public void deleteUser(String userId) {
        call(
                "deleteUser",
                () -> restClient
                        .delete()
                        .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .retrieve()
                        .toBodilessEntity());
    }

    @Override
    public void setUserEnabled(String userId, boolean enabled) {
        // Đọc rồi ghi lại cả representation để không xóa mất thuộc tính khác của user
        Map<String, Object> user = call(
                "setUserEnabled",
                () -> restClient
                        .get()
                        .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
        if (user == null) {
            throw new IdentityProviderException(
                    IdentityProviderErrorReason.UNAVAILABLE, "Keycloak returned empty user", null);
        }
        user.put("enabled", enabled);
        call(
                "setUserEnabled",
                () -> restClient
                        .put()
                        .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(user)
                        .retrieve()
                        .toBodilessEntity());
    }

    @Override
    public void resetTemporaryPassword(String userId, String temporaryPassword) {
        call(
                "resetTemporaryPassword",
                () -> restClient
                        .put()
                        .uri("/admin/realms/{realm}/users/{id}/reset-password", properties.realm(), userId)
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("type", "password", "value", temporaryPassword, "temporary", true))
                        .retrieve()
                        .toBodilessEntity());
    }

    @Override
    public void logoutUser(String userId) {
        call(
                "logoutUser",
                () -> restClient
                        .post()
                        .uri("/admin/realms/{realm}/users/{id}/logout", properties.realm(), userId)
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .retrieve()
                        .toBodilessEntity());
    }

    @Override
    public void sendSetupEmail(String userId) {
        call(
                "sendSetupEmail",
                () -> restClient
                        .put()
                        .uri("/admin/realms/{realm}/users/{id}/execute-actions-email", properties.realm(), userId)
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(List.of(UPDATE_PASSWORD))
                        .retrieve()
                        .toBodilessEntity());
    }

    @Override
    public boolean canSendSetupEmail() {
        return properties.actionsEmailEnabled();
    }

    private synchronized String adminToken() {
        Instant now = clock.instant();
        if (cachedAdminToken != null && now.isBefore(adminTokenExpiresAt)) {
            return cachedAdminToken;
        }
        if (!StringUtils.hasText(properties.adminClientSecret())) {
            throw new IdentityProviderException(
                    IdentityProviderErrorReason.UNAVAILABLE, "Keycloak admin client secret is not configured", null);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.adminClientId());
        form.add("client_secret", properties.adminClientSecret());

        TokenResponse token = restClient
                .post()
                .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (token == null || token.accessToken() == null) {
            throw new IdentityProviderException(
                    IdentityProviderErrorReason.UNAVAILABLE, "Keycloak returned empty admin token", null);
        }
        cachedAdminToken = token.accessToken();
        adminTokenExpiresAt = now.plusSeconds(token.expiresIn()).minus(TOKEN_EXPIRY_MARGIN);
        return cachedAdminToken;
    }

    private <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            log.warn("[{}]|keycloakStatus={}", operation, ex.getStatusCode().value());
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                // Token admin bị thu hồi/secret đổi: bỏ cache để lần gọi sau lấy token mới
                clearAdminToken();
            }
            throw new IdentityProviderException(
                    reasonOf(ex.getStatusCode().value()), "Keycloak " + operation + " failed", ex);
        } catch (RestClientException ex) {
            log.warn(
                    "[{}]|keycloakUnreachable|type={}", operation, ex.getClass().getSimpleName());
            throw new IdentityProviderException(
                    IdentityProviderErrorReason.UNAVAILABLE, "Keycloak " + operation + " failed", ex);
        }
    }

    private static IdentityProviderErrorReason reasonOf(int status) {
        if (status == HttpStatus.CONFLICT.value()) {
            return IdentityProviderErrorReason.CONFLICT;
        }
        return status == HttpStatus.BAD_REQUEST.value()
                ? IdentityProviderErrorReason.REJECTED
                : IdentityProviderErrorReason.UNAVAILABLE;
    }

    private synchronized void clearAdminToken() {
        cachedAdminToken = null;
        adminTokenExpiresAt = Instant.EPOCH;
    }

    private static SimpleClientHttpRequestFactory requestFactory(KeycloakProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RoleRepresentation(String id, String name) {}
}
