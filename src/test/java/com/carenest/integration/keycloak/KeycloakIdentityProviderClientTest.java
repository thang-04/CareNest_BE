package com.carenest.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.carenest.utils.enums.IdentityProviderErrorReason;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakIdentityProviderClientTest {

    private static final String BASE = "http://keycloak.test";
    private static final String TOKEN_URL = BASE + "/realms/carenest/protocol/openid-connect/token";
    private static final String USERS_URL = BASE + "/admin/realms/carenest/users";
    private static final String TOKEN_JSON =
            "{\"access_token\":\"admin-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}";

    private MockRestServiceServer server;
    private KeycloakIdentityProviderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakIdentityProviderClient(
                properties("backend-secret"),
                Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneOffset.UTC),
                builder);
    }

    @Test
    void createsUserWithTemporaryPasswordAndReturnsIdFromLocation() {
        expectAdminToken();
        server.expect(once(), requestTo(USERS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(jsonPath("$.username").value("teacher.c"))
                .andExpect(jsonPath("$.firstName").value("Giáo viên Demo"))
                .andExpect(jsonPath("$.requiredActions[0]").value("UPDATE_PASSWORD"))
                .andExpect(jsonPath("$.credentials[0].temporary").value(true))
                .andRespond(withCreatedEntity(URI.create(USERS_URL + "/kc-123")));

        String id = client.createUser(new NewIdentityUser("teacher.c", null, "Giáo viên Demo", "Temp-Pass-1"));

        assertThat(id).isEqualTo("kc-123");
        server.verify();
    }

    @Test
    void reusesCachedAdminTokenAcrossCalls() {
        expectAdminToken();
        server.expect(requestTo(USERS_URL + "/kc-1/logout")).andRespond(withSuccess());
        server.expect(requestTo(USERS_URL + "/kc-2/logout")).andRespond(withSuccess());

        client.logoutUser("kc-1");
        client.logoutUser("kc-2");

        server.verify();
    }

    @Test
    void duplicateUserMapsToConflict() {
        expectAdminToken();
        server.expect(requestTo(USERS_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.createUser(new NewIdentityUser("teacher.a", null, "Demo", "Temp-Pass-1")))
                .isInstanceOf(IdentityProviderException.class)
                .extracting("reason")
                .isEqualTo(IdentityProviderErrorReason.CONFLICT);
    }

    @Test
    void serverErrorMapsToUnavailable() {
        expectAdminToken();
        server.expect(requestTo(USERS_URL + "/kc-1")).andRespond(withServerError());

        assertThatThrownBy(() -> client.deleteUser("kc-1"))
                .isInstanceOf(IdentityProviderException.class)
                .extracting("reason")
                .isEqualTo(IdentityProviderErrorReason.UNAVAILABLE);
    }

    @Test
    void assignsRealmRoleUsingAvailableRoleRepresentation() {
        expectAdminToken();
        server.expect(requestTo(USERS_URL + "/kc-1/role-mappings/realm/available"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"id\":\"r-parent\",\"name\":\"PARENT\"},{\"id\":\"r-teacher\",\"name\":\"TEACHER\","
                                + "\"composite\":false}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERS_URL + "/kc-1/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0].id").value("r-teacher"))
                .andExpect(jsonPath("$[0].name").value("TEACHER"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.assignRealmRole("kc-1", "TEACHER");

        server.verify();
    }

    @Test
    void disablingUserKeepsOtherAttributes() {
        expectAdminToken();
        server.expect(requestTo(USERS_URL + "/kc-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"kc-1\",\"username\":\"teacher.c\",\"firstName\":\"Demo\",\"enabled\":true}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERS_URL + "/kc-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.firstName").value("Demo"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.setUserEnabled("kc-1", false);

        server.verify();
    }

    @Test
    void missingAdminSecretFailsWithoutCallingKeycloak() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer noCallsServer =
                MockRestServiceServer.bindTo(builder).build();
        KeycloakIdentityProviderClient unconfigured =
                new KeycloakIdentityProviderClient(properties(""), Clock.systemUTC(), builder);

        assertThatThrownBy(() -> unconfigured.deleteUser("kc-1"))
                .isInstanceOf(IdentityProviderException.class)
                .extracting("reason")
                .isEqualTo(IdentityProviderErrorReason.UNAVAILABLE);
        noCallsServer.verify();
    }

    private void expectAdminToken() {
        server.expect(once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content()
                        .formDataContains(Map.of(
                                "grant_type",
                                "client_credentials",
                                "client_id",
                                "carenest-backend",
                                "client_secret",
                                "backend-secret")))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
    }

    private static KeycloakProperties properties(String secret) {
        return new KeycloakProperties(
                BASE, "carenest", "carenest-backend", secret, false, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    @Test
    void rejectedAccountDataMapsToRejected() {
        expectAdminToken();
        server.expect(requestTo(USERS_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.createUser(new NewIdentityUser("teacher.c", null, "Demo", "teacher.c")))
                .isInstanceOf(IdentityProviderException.class)
                .extracting("reason")
                .isEqualTo(IdentityProviderErrorReason.REJECTED);
    }

    @Test
    void revokedAdminTokenIsDroppedSoNextCallFetchesNewOne() {
        expectAdminToken();
        server.expect(requestTo(USERS_URL + "/kc-1/logout")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectAdminToken();
        server.expect(requestTo(USERS_URL + "/kc-1/logout")).andRespond(withSuccess());

        assertThatThrownBy(() -> client.logoutUser("kc-1")).isInstanceOf(IdentityProviderException.class);
        client.logoutUser("kc-1");

        server.verify();
    }
}
