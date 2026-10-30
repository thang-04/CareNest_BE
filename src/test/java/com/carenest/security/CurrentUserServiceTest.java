package com.carenest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carenest.entity.account.RolePermission;
import com.carenest.entity.account.UserAccount;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.RolePermissionRepository;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.repository.account.UserPermissionOverrideRepository;
import com.carenest.security.authorization.TeamLeaderAssignmentChecker;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import com.carenest.utils.enums.UserStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    private static final String KEYCLOAK_USER_ID = "8f6a3c1e-0000-4000-8000-000000000001";

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    @Mock
    private TeamLeaderAssignmentChecker teamLeaderAssignmentChecker;

    @InjectMocks
    private CurrentUserService currentUserService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesProvisionedActiveAccountFromJwtReturnedByVerifiedDecoder() {
        UserAccount account = UserAccount.create("kc-web-user", "teacher.web", "web@example.test", "Giáo viên Web");
        ReflectionTestUtils.setField(account, "id", 47L);
        when(userAccountRepository.findByKeycloakUserId("kc-web-user")).thenReturn(Optional.of(account));
        Jwt jwt = Jwt.withTokenValue("validated-web-token")
                .header("alg", "RS256")
                .subject("kc-web-user")
                .issuedAt(Instant.parse("2026-10-12T01:00:00Z"))
                .expiresAt(Instant.parse("2026-10-12T01:05:00Z"))
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("TEACHER")))
                .build();
        var currentUser = currentUserService.getCurrentUser(jwt);
        assertThat(currentUser.userId()).isEqualTo(47L);
        assertThat(currentUser.roles()).containsExactly(CareNestRole.TEACHER);
        assertThat(currentUser.fullName()).isEqualTo("Giáo viên Web");
    }

    @Test
    void shouldBuildCurrentUserFromJwtSubjectAndTokenRoles() {
        authenticateWithJwt("ROLE_TEACHER", "SCOPE_profile");
        given(userAccountRepository.findByKeycloakUserId(KEYCLOAK_USER_ID))
                .willReturn(Optional.of(account(UserStatus.ACTIVE)));

        CurrentUser currentUser = currentUserService.getCurrentUser();

        assertThat(currentUser.userId()).isEqualTo(10L);
        assertThat(currentUser.keycloakUserId()).isEqualTo(KEYCLOAK_USER_ID);
        assertThat(currentUser.username()).isEqualTo("teacher.a");
        assertThat(currentUser.roles()).containsExactly(CareNestRole.TEACHER);
    }

    @Test
    @DisplayName("AUTH-10: quyền giáo viên đọc từ ma trận + ngoại lệ ở mỗi request, REVOKE có hiệu lực ngay")
    void shouldResolveTeacherPermissionsFromMatrixAndOverrides() {
        authenticateWithJwt("ROLE_TEACHER");
        given(userAccountRepository.findByKeycloakUserId(KEYCLOAK_USER_ID))
                .willReturn(Optional.of(account(UserStatus.ACTIVE)));
        given(rolePermissionRepository.findByRoleIn(Set.of(CareNestRole.TEACHER)))
                .willReturn(List.of(RolePermission.create(CareNestRole.TEACHER, "CHILD_PROFILE_VIEW", null)));
        given(userPermissionOverrideRepository.findByUserId(10L)).willReturn(List.of());

        assertThat(currentUserService.getCurrentUser().permissions()).containsExactly(Permission.CHILD_PROFILE_VIEW);

        given(userPermissionOverrideRepository.findByUserId(10L))
                .willReturn(List.of(UserPermissionOverride.create(
                        10L, "CHILD_PROFILE_VIEW", PermissionEffect.REVOKE, "Tạm nghỉ", 1L)));

        assertThat(currentUserService.getCurrentUser().permissions()).isEmpty();
    }

    @Test
    @DisplayName("AUTH-23: GV đang có phân công tổ trưởng đọc thêm cột TEAM_LEADER của ma trận, role token giữ nguyên")
    void activeTeamLeaderAlsoReadsTeamLeaderMatrixColumn() {
        authenticateWithJwt("ROLE_TEACHER");
        given(userAccountRepository.findByKeycloakUserId(KEYCLOAK_USER_ID))
                .willReturn(Optional.of(account(UserStatus.ACTIVE)));
        given(teamLeaderAssignmentChecker.isActiveTeamLeader(10L, Set.of(CareNestRole.TEACHER)))
                .willReturn(true);
        given(rolePermissionRepository.findByRoleIn(Set.of(CareNestRole.TEACHER, CareNestRole.TEAM_LEADER)))
                .willReturn(List.of(RolePermission.create(CareNestRole.TEACHER, "CHILD_PROFILE_VIEW", null)));
        given(userPermissionOverrideRepository.findByUserId(10L)).willReturn(List.of());

        CurrentUser currentUser = currentUserService.getCurrentUser();

        assertThat(currentUser.roles()).containsExactly(CareNestRole.TEACHER);
        assertThat(currentUser.permissions()).containsExactly(Permission.CHILD_PROFILE_VIEW);
    }

    @Test
    @DisplayName("AUTH-23: token tự mang role TEAM_LEADER không được nhận")
    void teamLeaderRoleInTokenIsIgnored() {
        authenticateWithJwt("ROLE_TEAM_LEADER");
        given(userAccountRepository.findByKeycloakUserId(KEYCLOAK_USER_ID))
                .willReturn(Optional.of(account(UserStatus.ACTIVE)));

        CurrentUser currentUser = currentUserService.getCurrentUser();

        assertThat(currentUser.roles()).isEmpty();
        assertThat(currentUser.permissions()).isEmpty();
        verifyNoInteractions(rolePermissionRepository, userPermissionOverrideRepository);
    }

    @Test
    @DisplayName("AUTH-11: quyền PRINCIPAL cố định trong code, không đọc ma trận")
    void shouldGivePrincipalFixedPermissionsWithoutQueryingMatrix() {
        authenticateWithJwt("ROLE_PRINCIPAL");
        given(userAccountRepository.findByKeycloakUserId(KEYCLOAK_USER_ID))
                .willReturn(Optional.of(account(UserStatus.ACTIVE)));

        assertThat(currentUserService.getCurrentUser().permissions())
                .containsExactlyInAnyOrder(
                        Permission.CHILD_PROFILE_VIEW,
                        Permission.ACCOUNT_MANAGE,
                        Permission.ACCOUNT_STATUS_MANAGE,
                        Permission.STAFF_ASSIGNMENT_MANAGE,
                        Permission.PARENT_ACCOUNT_MANAGE,
                        Permission.PERMISSION_MANAGE);
        verifyNoInteractions(rolePermissionRepository, userPermissionOverrideRepository);
    }

    @Test
    void shouldRejectAuthenticatedUserWithoutCareNestAccount() {
        authenticateWithJwt("ROLE_TEACHER");
        given(userAccountRepository.findByKeycloakUserId(KEYCLOAK_USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.ACCOUNT_NOT_PROVISIONED.getDefaultDesc())
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.FORBIDDEN.getHttpStatus());
    }

    @Test
    void shouldRejectInactiveAccountEvenWithValidToken() {
        authenticateWithJwt("ROLE_TEACHER");
        given(userAccountRepository.findByKeycloakUserId(KEYCLOAK_USER_ID))
                .willReturn(Optional.of(account(UserStatus.INACTIVE)));

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.ACCOUNT_INACTIVE.getDefaultDesc())
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.FORBIDDEN.getHttpStatus());
    }

    @Test
    void shouldRejectRequestWithoutJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "n/a"));

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.UNAUTHORIZED.getHttpStatus());
    }

    private static void authenticateWithJwt(String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(KEYCLOAK_USER_ID)
                .build();
        List<SimpleGrantedAuthority> grantedAuthorities =
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, grantedAuthorities));
    }

    private static UserAccount account(UserStatus status) {
        UserAccount account = UserAccount.create(KEYCLOAK_USER_ID, "teacher.a", null, "Giáo viên Demo");
        ReflectionTestUtils.setField(account, "id", 10L);
        ReflectionTestUtils.setField(account, "status", status);
        return account;
    }
}
