package com.carenest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.RolePermissionRepository;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.repository.account.UserPermissionOverrideRepository;
import com.carenest.security.authorization.TeamLeaderAssignmentChecker;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

class CurrentUserServicePasswordChangeTest {
    private static final Instant CHANGED_AT = Instant.parse("2026-11-23T03:00:00.700Z");

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final KeycloakRealmRoleConverter roleConverter = mock(KeycloakRealmRoleConverter.class);
    private final CurrentUserService service = new CurrentUserService(
            accounts,
            mock(RolePermissionRepository.class),
            mock(UserPermissionOverrideRepository.class),
            mock(TeamLeaderAssignmentChecker.class),
            roleConverter);
    private final UserAccount account = UserAccount.create("kc-1", "0912345678", null, "Giáo viên Demo");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(account, "id", 1L);
        given(accounts.findByKeycloakUserId("kc-1")).willReturn(Optional.of(account));
        given(roleConverter.convert(any())).willReturn(List.of());
    }

    @Test
    @DisplayName("Token cấp trước lần đổi mật khẩu gần nhất ⇒ 401/1002")
    void tokenIssuedBeforePasswordChangeIsRejected() {
        ReflectionTestUtils.setField(account, "passwordChangedAt", CHANGED_AT);

        assertThatThrownBy(() -> service.getCurrentUser(jwt(CHANGED_AT.minusSeconds(1))))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("Token cấp cùng giây hoặc sau lần đổi, hoặc chưa từng đổi ⇒ qua")
    void tokenIssuedInOrAfterTheChangeSecondPasses() {
        assertThat(service.getCurrentUser(jwt(CHANGED_AT.minusSeconds(60))).userId())
                .isEqualTo(1L);
        ReflectionTestUtils.setField(account, "passwordChangedAt", CHANGED_AT);
        assertThat(service.getCurrentUser(jwt(Instant.parse("2026-11-23T03:00:00Z")))
                        .userId())
                .isEqualTo(1L);
        assertThat(service.getCurrentUser(jwt(CHANGED_AT.plusSeconds(5))).userId())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Tài khoản bị khóa vẫn trả 1015 dù token cũng cấp trước lần đổi mật khẩu")
    void inactiveAccountKeepsItsOwnCode() {
        ReflectionTestUtils.setField(account, "passwordChangedAt", CHANGED_AT);
        account.changeStatus(UserStatus.INACTIVE);

        assertThatThrownBy(() -> service.getCurrentUser(jwt(CHANGED_AT.minusSeconds(1))))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.ACCOUNT_INACTIVE));
    }

    private static Jwt jwt(Instant issuedAt) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("kc-1")
                .issuedAt(issuedAt)
                .build();
    }
}
