package com.carenest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.carenest.entity.account.UserAccount;
import com.carenest.entity.account.UserStatus;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.utils.ApiCode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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

    @InjectMocks
    private CurrentUserService currentUserService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
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
    void shouldRejectAuthenticatedUserWithoutCareNestAccount() {
        authenticateWithJwt("ROLE_TEACHER");
        given(userAccountRepository.findByKeycloakUserId(KEYCLOAK_USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(GlobalException.class)
                .hasMessage("Account is not provisioned")
                .extracting("apiCode").isEqualTo(ApiCode.FORBIDDEN);
    }

    @Test
    void shouldRejectInactiveAccountEvenWithValidToken() {
        authenticateWithJwt("ROLE_TEACHER");
        given(userAccountRepository.findByKeycloakUserId(KEYCLOAK_USER_ID))
                .willReturn(Optional.of(account(UserStatus.INACTIVE)));

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(GlobalException.class)
                .hasMessage("Account is inactive")
                .extracting("apiCode").isEqualTo(ApiCode.FORBIDDEN);
    }

    @Test
    void shouldRejectRequestWithoutJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "n/a"));

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode").isEqualTo(ApiCode.UNAUTHORIZED);
    }

    private static void authenticateWithJwt(String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(KEYCLOAK_USER_ID).build();
        List<SimpleGrantedAuthority> grantedAuthorities = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, grantedAuthorities));
    }

    private static UserAccount account(UserStatus status) {
        UserAccount account = UserAccount.create(KEYCLOAK_USER_ID, "teacher.a", null, "Giáo viên Demo");
        ReflectionTestUtils.setField(account, "id", 10L);
        ReflectionTestUtils.setField(account, "status", status);
        return account;
    }
}
