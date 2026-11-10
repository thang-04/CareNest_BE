package com.carenest.security;

import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ClientPlatform;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final String AUTHORIZED_PARTY_CLAIM = "azp";
    private static final List<GrantedAuthority> DENIED =
            List.of(new SimpleGrantedAuthority(ClientPlatform.DENIED_AUTHORITY));

    private final ClientPlatformProperties platforms;

    public KeycloakRealmRoleConverter(ClientPlatformProperties platforms) {
        this.platforms = platforms;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // AUTH-21: GV kiêm phụ huynh trên Web chỉ còn role GV; phụ huynh/bếp trên Web, BGH trên App không còn role nào
        Set<CareNestRole> allowed = platforms
                .platformOf(jwt.getClaimAsString(AUTHORIZED_PARTY_CLAIM))
                .map(ClientPlatform::allowedRoles)
                .orElse(Set.of());
        List<GrantedAuthority> authorities = realmRoles(jwt).stream()
                .filter(allowed::contains)
                .distinct()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();
        return authorities.isEmpty() ? DENIED : authorities;
    }

    private static List<CareNestRole> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null || !(realmAccess.get(ROLES_KEY) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(CareNestRole::fromKeycloakRole)
                .flatMap(Optional::stream)
                .toList();
    }
}
