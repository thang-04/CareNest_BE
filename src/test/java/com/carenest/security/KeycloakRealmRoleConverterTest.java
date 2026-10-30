package com.carenest.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void shouldMapCareNestRealmRolesToSpringAuthorities() {
        Jwt jwt = jwtWithClaim(
                "realm_access",
                Map.of("roles", List.of("TEACHER", "PARENT", "offline_access", "default-roles-carenest")));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_TEACHER", "ROLE_PARENT");
    }

    @Test
    @DisplayName("AUTH-23: realm role TEAM_LEADER trong token không thành authority")
    void shouldIgnoreTeamLeaderRealmRole() {
        Jwt jwt = jwtWithClaim("realm_access", Map.of("roles", List.of("TEACHER", "TEAM_LEADER")));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_TEACHER");
    }

    @Test
    void shouldReturnNoAuthorityWhenRealmAccessIsMissing() {
        Jwt jwt = jwtWithClaim("preferred_username", "teacher.a");

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void shouldReturnNoAuthorityWhenRolesIsNotAList() {
        Jwt jwt = jwtWithClaim("realm_access", Map.of("roles", "TEACHER"));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private static Jwt jwtWithClaim(String name, Object value) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("kc-user-1")
                .claim(name, value)
                .build();
    }
}
