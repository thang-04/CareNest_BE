package com.carenest.security;

import com.carenest.entity.account.RolePermission;
import com.carenest.entity.account.UserAccount;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.RolePermissionRepository;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.repository.account.UserPermissionOverrideRepository;
import com.carenest.security.authorization.EffectivePermissionResolver;
import com.carenest.security.authorization.TeamLeaderAssignmentChecker;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nơi duy nhất đọc JWT để xác định người dùng hiện tại; service nghiệp vụ không tự parse token. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final TeamLeaderAssignmentChecker teamLeaderAssignmentChecker;
    private final KeycloakRealmRoleConverter realmRoleConverter;

    @Transactional(readOnly = true)
    public CurrentUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw GlobalException.of(ApiCode.UNAUTHENTICATED);
        }

        return getCurrentUser(jwtAuthentication.getToken(), rolesOf(authentication));
    }

    /** Chỉ gọi sau JwtDecoder đã kiểm chữ ký, issuer, audience và thời hạn. */
    @Transactional(readOnly = true)
    public CurrentUser getCurrentUser(Jwt validatedToken) {
        if (validatedToken == null || validatedToken.getSubject() == null) {
            throw GlobalException.of(ApiCode.UNAUTHENTICATED);
        }
        JwtAuthenticationToken verifiedAuthentication =
                new JwtAuthenticationToken(validatedToken, realmRoleConverter.convert(validatedToken));
        return getCurrentUser(validatedToken, rolesOf(verifiedAuthentication));
    }

    private CurrentUser getCurrentUser(Jwt validatedToken, Set<CareNestRole> roles) {
        // Danh tính chỉ lấy từ "sub" đã được kiểm chữ ký, không lấy từ id client gửi
        String keycloakUserId = validatedToken.getSubject();
        UserAccount account = userAccountRepository
                .findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> {
                    log.warn("[getCurrentUser]|keycloakUserId={}|reason=notProvisioned", keycloakUserId);
                    return GlobalException.of(ApiCode.ACCOUNT_NOT_PROVISIONED);
                });
        // Token còn hạn vẫn bị chặn khi CareNest đã khóa tài khoản
        if (!account.isActive()) {
            log.warn("[getCurrentUser]|userId={}|reason=inactive", account.getId());
            throw GlobalException.of(ApiCode.ACCOUNT_INACTIVE);
        }

        Set<CareNestRole> permissionRoles = EffectivePermissionResolver.permissionRoles(
                roles, teamLeaderAssignmentChecker.isActiveTeamLeader(account.getId(), roles));
        Set<CareNestRole> configurableRoles =
                permissionRoles.stream().filter(Permission::isConfigurableRole).collect(Collectors.toSet());
        // Đọc DB mỗi request nên HT đổi quyền là có hiệu lực ngay, không chờ token mới (ADR-0017)
        List<RolePermission> matrixRows =
                configurableRoles.isEmpty() ? List.of() : rolePermissionRepository.findByRoleIn(configurableRoles);
        List<UserPermissionOverride> overrides = configurableRoles.isEmpty()
                ? List.of()
                : userPermissionOverrideRepository.findByUserId(account.getId());
        return new CurrentUser(
                account.getId(),
                keycloakUserId,
                account.getUsername(),
                account.getFullName(),
                account.getEmail(),
                roles,
                EffectivePermissionResolver.resolve(permissionRoles, matrixRows, overrides),
                EffectivePermissionResolver.schoolWide(permissionRoles, overrides));
    }

    private static Set<CareNestRole> rolesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(CareNestRole::fromAuthority)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }
}
