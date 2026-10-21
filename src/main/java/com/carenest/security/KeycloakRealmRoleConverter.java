package com.carenest.security;

import com.carenest.utils.enums.CareNestRole;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Đọc realm role trong claim {@code realm_access.roles} của Keycloak thành {@code ROLE_*}.
 * Chỉ nhận role CareNest đã khai báo; role mặc định của Keycloak (offline_access...) bị bỏ qua.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null || !(realmAccess.get(ROLES_KEY) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(CareNestRole::fromKeycloakRole)
                .flatMap(Optional::stream)
                .distinct()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();
    }
}
