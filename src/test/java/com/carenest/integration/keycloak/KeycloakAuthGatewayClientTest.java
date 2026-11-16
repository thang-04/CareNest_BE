package com.carenest.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.carenest.config.AuthProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakAuthGatewayClientTest {

    @Test
    void revokedRefreshIsSessionRejectionRatherThanProviderOutage() {
        server.expect(requestTo(TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));
        assertThatThrownBy(() -> client.refresh("revoked-refresh"))
                .isInstanceOf(AuthGatewayClient.SessionRejected.class);
        server.verify();
    }

    private static final String BASE = "http://keycloak.test";
    private static final String TOKEN = BASE + "/realms/carenest/protocol/openid-connect/token";
    private final AuthProperties web = new AuthProperties(
            false,
            "",
            "",
            "carenest-auth-gateway",
            "gateway-secret-unit-test-32chars-min",
            false,
            java.util.List.of("http://localhost:5173"));
    private final KeycloakProperties admin = new KeycloakProperties(
            BASE,
            "carenest",
            "carenest-backend",
            "admin-test-secret-at-least-32-characters",
            true,
            Duration.ofSeconds(3),
            Duration.ofSeconds(5));
    private MockRestServiceServer server;
    private KeycloakAuthGatewayClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        client = new KeycloakAuthGatewayClient(web, admin, builder);
    }

    @Test
    void sendsDirectGrantOnlyFromTheBackendGatewayAndParsesTokens() {
        server.expect(requestTo(TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("client_id=carenest-auth-gateway"),
                                org.hamcrest.Matchers.containsString(
                                        "client_secret=gateway-secret-unit-test-32chars-min"),
                                org.hamcrest.Matchers.containsString("grant_type=password"),
                                org.hamcrest.Matchers.containsString("username=teacher.a"))))
                .andRespond(withSuccess("""
                    {"access_token":"access-unit","refresh_token":"refresh-unit","expires_in":300,"refresh_expires_in":36000}
                    """, MediaType.APPLICATION_JSON));
        var tokens = client.login("teacher.a", "password-value-is-never-logged");
        assertThat(tokens.accessToken()).isEqualTo("access-unit");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-unit");
        assertThat(tokens.expiresIn()).isEqualTo(300);
        assertThat(tokens.toString()).doesNotContain("access-unit", "refresh-unit");
        server.verify();
    }

    @Test
    void mapsTypedFirstLoginChallengeWithoutReadingHumanReadableErrorText() {
        String body = """
                {"error":"carenest_password_change_required","user_id":"123e4567-e89b-42d3-a456-426614174000","setup_token":"%s","expires_in":600,"error_description":"sensitive server wording"}
                """.formatted("a".repeat(43));
        server.expect(requestTo(TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));
        assertThatThrownBy(() -> client.login("teacher.a", "issued-temporary-value"))
                .isInstanceOf(AuthGatewayClient.FirstLoginRequired.class)
                .hasMessage("Password change required")
                .satisfies(error -> assertThat(error.toString())
                        .doesNotContain("issued-temporary-value", "sensitive server wording"));
        server.verify();
    }

    @Test
    void wrongPasswordIsGeneric() {
        server.expect(requestTo(TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                    {"error":"invalid_grant","error_description":"invalid credentials"}
                    """));
        assertThatThrownBy(() -> client.login("teacher.a", "wrong-value"))
                .isInstanceOf(AuthGatewayClient.InvalidCredentials.class)
                .hasMessage("Invalid credentials");
        server.verify();
    }

    @Test
    void unconfiguredGatewayIsNotReportedAsWrongPassword() {
        server.expect(requestTo(TOKEN))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                    {"error":"invalid_client"}
                    """));
        assertThatThrownBy(() -> client.login("teacher.a", "wrong-value"))
                .isInstanceOf(AuthGatewayClient.ProviderUnavailable.class);
        server.verify();
    }

    @Test
    void rotatesRefreshTokenAndLogsOutThePrivateWebSession() {
        server.expect(requestTo(TOKEN))
                .andRespond(withSuccess(
                        "{\"access_token\":\"access-next\",\"refresh_token\":\"refresh-next\",\"expires_in\":300,\"refresh_expires_in\":35400}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/realms/carenest/protocol/openid-connect/logout"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=logout")))
                .andRespond(withSuccess());
        assertThat(client.refresh("refresh-old").refreshToken()).isEqualTo("refresh-next");
        client.logout("refresh-next");
        server.verify();
    }

    @Test
    void malformedProviderErrorIsNotReportedAsWrongPassword() {
        server.expect(requestTo(TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("not-json"));
        assertThatThrownBy(() -> client.login("teacher.a", "wrong-value"))
                .isInstanceOf(AuthGatewayClient.ProviderUnavailable.class);
        server.verify();
    }

    @Test
    void invalidClientBadRequestIsNotReportedAsWrongPassword() {
        server.expect(requestTo(TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_client\"}"));
        assertThatThrownBy(() -> client.login("teacher.a", "wrong-value"))
                .isInstanceOf(AuthGatewayClient.ProviderUnavailable.class);
        server.verify();
    }

    @Test
    void exchangesVerifiedProofWithServiceAccountAndReusesOnlyItsAdminToken() {
        expectAdminToken();
        server.expect(requestTo(BASE + "/realms/carenest/carenest-auth/password/capability"))
                .andExpect(header("Authorization", "Bearer admin-access-unit"))
                .andExpect(content().json("{\"assertion\":\"signed-unit-proof\"}"))
                .andRespond(withSuccess("{\"token\":\"" + "a".repeat(43) + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/realms/carenest/carenest-auth/recovery/email"))
                .andExpect(header("Authorization", "Bearer admin-access-unit"))
                .andExpect(content().json("{\"userId\":\"kc-unit\",\"code\":\"123456\"}"))
                .andRespond(withSuccess());
        assertThat(client.verifiedCapability("signed-unit-proof")).isEqualTo("a".repeat(43));
        client.sendOtp("kc-unit", "123456");
        server.verify();
    }

    @Test
    void mapsPolicyRejectionWithoutRevealingProviderDescription() {
        expectAdminToken();
        server.expect(requestTo(BASE + "/realms/carenest/carenest-auth/password/setup"))
                .andExpect(header("Authorization", "Bearer admin-access-unit"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"password_policy_rejected\",\"error_description\":\"private details\"}"));
        assertThatThrownBy(() -> client.changePassword("a".repeat(43), "new-unit-password", "new-unit-password"))
                .isInstanceOf(AuthGatewayClient.PasswordRejected.class)
                .hasMessage("Password rejected by provider policy");
        server.verify();
    }

    @Test
    void invalidCapabilityIsNotReportedAsPolicyRejection() {
        expectAdminToken();
        server.expect(requestTo(BASE + "/realms/carenest/carenest-auth/password/setup"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"capability_invalid\"}"));
        assertThatThrownBy(() -> client.changePassword("a".repeat(43), "new-unit-password", "new-unit-password"))
                .isInstanceOf(AuthGatewayClient.CapabilityRejected.class);
        server.verify();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("AUTH-28: hủy phiên Web cũ, giữ phiên vừa đăng nhập và phiên Mobile")
    void revokesOnlyOtherWebGatewaySessions() {
        expectAdminToken();
        server.expect(requestTo(BASE + "/admin/realms/carenest/users/kc-unit/sessions"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer admin-access-unit"))
                .andRespond(withSuccess("""
                    [{"id":"new-web","clients":{"c1":"carenest-auth-gateway"}},
                     {"id":"old-web","clients":{"c1":"carenest-auth-gateway"}},
                     {"id":"mobile","clients":{"c2":"carenest-mobile"}}]
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/admin/realms/carenest/sessions/old-web"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer admin-access-unit"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        assertThat(client.revokeOtherWebSessions("kc-unit", "new-web")).containsExactly("old-web");
        server.verify();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "AUTH-28: chỉ phiên Web khác mới tính là xung đột; phiên Mobile không tính, lỗi tra cứu ⇒ không hỏi")
    void detectsOtherWebSessionsIgnoringMobile() {
        expectAdminToken();
        server.expect(requestTo(BASE + "/admin/realms/carenest/users/kc-unit/sessions"))
                .andRespond(withSuccess("""
                    [{"id":"new-web","clients":{"c1":"carenest-auth-gateway"}},
                     {"id":"mobile","clients":{"c2":"carenest-mobile"}}]
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/admin/realms/carenest/users/kc-unit/sessions"))
                .andRespond(withSuccess("""
                    [{"id":"new-web","clients":{"c1":"carenest-auth-gateway"}},
                     {"id":"old-web","clients":{"c1":"carenest-auth-gateway"}}]
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/admin/realms/carenest/users/kc-unit/sessions"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(client.hasOtherWebSessions("kc-unit", "new-web")).isFalse();
        assertThat(client.hasOtherWebSessions("kc-unit", "new-web")).isTrue();
        assertThat(client.hasOtherWebSessions("kc-unit", "new-web")).isFalse();
        server.verify();
    }

    @Test
    void sessionRevokeFailureDoesNotFailTheLogin() {
        expectAdminToken();
        server.expect(requestTo(BASE + "/admin/realms/carenest/users/kc-unit/sessions"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(client.revokeOtherWebSessions("kc-unit", "new-web")).isEmpty();
        server.verify();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("AUTH-28: có phiên Web mới hơn ⇒ phiên bị từ chối là do đăng nhập nơi khác")
    void detectsNewerWebSessionForRejectedRefreshToken() {
        expectAdminToken();
        server.expect(requestTo(BASE + "/admin/realms/carenest/users/kc-unit/sessions"))
                .andRespond(withSuccess("""
                    [{"id":"new-web","start":2000000,"clients":{"c1":"carenest-auth-gateway"}}]
                    """, MediaType.APPLICATION_JSON));
        assertThat(client.replacedByNewerWebSession(refreshToken("kc-unit", "old-web"), Instant.ofEpochSecond(1000)))
                .isTrue();
        server.verify();
    }

    @Test
    void onlyNewerMobileOrOlderSessionsDoNotCountAsReplacement() {
        expectAdminToken();
        server.expect(requestTo(BASE + "/admin/realms/carenest/users/kc-unit/sessions"))
                .andRespond(withSuccess("""
                    [{"id":"mobile","start":2000000,"clients":{"c2":"carenest-mobile"}},
                     {"id":"older-web","start":500000,"clients":{"c1":"carenest-auth-gateway"}}]
                    """, MediaType.APPLICATION_JSON));
        assertThat(client.replacedByNewerWebSession(refreshToken("kc-unit", "old-web"), Instant.ofEpochSecond(1000)))
                .isFalse();
        server.verify();
    }

    @Test
    void malformedRefreshTokenIsNeverReportedAsReplaced() {
        assertThat(client.replacedByNewerWebSession("not-a-jwt", Instant.EPOCH)).isFalse();
        assertThat(client.replacedByNewerWebSession("a.%%%.c", Instant.EPOCH)).isFalse();
        server.verify();
    }

    private static String refreshToken(String sub, String sid) {
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        String payload = "{\"sub\":\"" + sub + "\",\"sid\":\"" + sid + "\",\"typ\":\"Refresh\"}";
        return encoder.encodeToString("{\"alg\":\"HS512\"}".getBytes()) + "."
                + encoder.encodeToString(payload.getBytes()) + ".signature";
    }

    private void expectAdminToken() {
        server.expect(requestTo(TOKEN))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=carenest-backend")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"admin-access-unit\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    }
}
