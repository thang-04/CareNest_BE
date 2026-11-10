package com.carenest.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.utils.enums.ClientPlatform;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    static final ClientPlatformProperties PLATFORMS = new ClientPlatformProperties(
            List.of("carenest-auth-gateway", "carenest-web"), List.of("carenest-mobile"), List.of("carenest-dev"));

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter(PLATFORMS);

    @Test
    void shouldMapCareNestRealmRolesToSpringAuthorities() {
        Jwt jwt = jwt(
                "carenest-mobile",
                Map.of("roles", List.of("TEACHER", "PARENT", "offline_access", "default-roles-carenest")));

        assertThat(authorities(jwt)).containsExactlyInAnyOrder("ROLE_TEACHER", "ROLE_PARENT");
    }

    @Test
    @DisplayName("AUTH-23: realm role TEAM_LEADER trong token không thành authority")
    void shouldIgnoreTeamLeaderRealmRole() {
        Jwt jwt = jwt("carenest-auth-gateway", Map.of("roles", List.of("TEACHER", "TEAM_LEADER")));

        assertThat(authorities(jwt)).containsExactly("ROLE_TEACHER");
    }

    @Test
    @DisplayName("AUTH-21: Web bỏ role PARENT và KITCHEN_STAFF, giữ role nhân sự")
    void webTokenKeepsOnlyWebRoles() {
        Jwt jwt = jwt("carenest-web", Map.of("roles", List.of("VICE_PRINCIPAL", "PARENT", "KITCHEN_STAFF")));

        assertThat(authorities(jwt)).containsExactly("ROLE_VICE_PRINCIPAL");
    }

    @Test
    @DisplayName("AUTH-21: App bỏ role Ban giám hiệu")
    void mobileTokenDropsSchoolBoardRoles() {
        Jwt jwt = jwt("carenest-mobile", Map.of("roles", List.of("PRINCIPAL", "TEACHER")));

        assertThat(authorities(jwt)).containsExactly("ROLE_TEACHER");
    }

    @Test
    @DisplayName("AUTH-21: không còn role nào trên nền tảng ⇒ authority từ chối")
    void parentOnlyWebTokenIsDenied() {
        Jwt jwt = jwt("carenest-auth-gateway", Map.of("roles", List.of("PARENT")));

        assertThat(authorities(jwt)).containsExactly(ClientPlatform.DENIED_AUTHORITY);
    }

    @Test
    void devClientKeepsEveryRole() {
        Jwt jwt = jwt("carenest-dev", Map.of("roles", List.of("PRINCIPAL", "PARENT")));

        assertThat(authorities(jwt)).containsExactlyInAnyOrder("ROLE_PRINCIPAL", "ROLE_PARENT");
    }

    @Test
    void unknownOrMissingClientIsDenied() {
        Map<String, Object> roles = Map.of("roles", List.of("TEACHER"));

        assertThat(authorities(jwt("other-app", roles))).containsExactly(ClientPlatform.DENIED_AUTHORITY);
        assertThat(authorities(jwt(null, roles))).containsExactly(ClientPlatform.DENIED_AUTHORITY);
    }

    @Test
    void shouldDenyWhenRealmAccessIsMissingOrMalformed() {
        assertThat(authorities(jwt("carenest-mobile", null))).containsExactly(ClientPlatform.DENIED_AUTHORITY);
        assertThat(authorities(jwt("carenest-mobile", Map.of("roles", "TEACHER"))))
                .containsExactly(ClientPlatform.DENIED_AUTHORITY);
    }

    @Test
    void webClientOfGatewayConfigIsAdded() {
        KeycloakRealmRoleConverter custom =
                new KeycloakRealmRoleConverter(PLATFORMS.withWebClient("school-web-gateway"));
        Jwt jwt = jwt("school-web-gateway", Map.of("roles", List.of("TEACHER")));

        assertThat(custom.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_TEACHER");
    }

    private List<String> authorities(Jwt jwt) {
        return converter.convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private static Jwt jwt(String clientId, Map<String, Object> realmAccess) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("kc-user-1");
        if (clientId != null) builder.claim("azp", clientId);
        if (realmAccess != null) builder.claim("realm_access", realmAccess);
        else builder.claim("preferred_username", "teacher.a");
        return builder.build();
    }
}
