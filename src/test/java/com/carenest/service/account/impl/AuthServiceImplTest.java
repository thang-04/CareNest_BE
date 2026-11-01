package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.carenest.config.AuthProperties;
import com.carenest.dto.account.AuthRequests;
import com.carenest.dto.account.MyAccountResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.AuthGatewayClient;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.AuthCrypto;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.service.account.AccountService;
import com.carenest.service.account.AuthStateStore;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.PasswordTokenPurpose;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthGatewayClient keycloak;

    @Mock
    private AuthStateStore state;

    @Mock
    private UserAccountRepository userAccounts;

    @Mock
    private JwtDecoder decoder;

    @Mock
    private CurrentUserService currentUsers;

    @Mock
    private AccountService accounts;

    @InjectMocks
    private AuthServiceImpl service;

    private AuthCrypto crypto;
    private UserAccount account;
    private CurrentUser current;
    private MyAccountResponse profile;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() throws Exception {
        var properties = new AuthProperties(
                true,
                key(),
                key(),
                "carenest-auth-gateway",
                "gateway-secret-at-least-32-characters",
                false,
                List.of("http://localhost:5173"));
        crypto = new AuthCrypto(properties);
        account = UserAccount.create("kc-user-1", "teacher.a", "teacher@example.test", "Giáo viên Demo");
        Field id = UserAccount.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(account, 91L);
        current = new CurrentUser(
                91L, "kc-user-1", "teacher.a", "Giáo viên Demo", "teacher@example.test", Set.of(CareNestRole.TEACHER));
        profile = new MyAccountResponse(
                91L,
                "teacher.a",
                "Giáo viên Demo",
                "teacher@example.test",
                List.of(CareNestRole.TEACHER),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        service = new AuthServiceImpl(
                new AuthProperties(
                        true,
                        key(),
                        key(),
                        "carenest-auth-gateway",
                        "gateway-secret-at-least-32-characters",
                        false,
                        List.of("http://localhost:5173")),
                crypto,
                keycloak,
                userAccounts,
                state,
                decoder,
                currentUsers,
                accounts,
                clock,
                new com.carenest.integration.keycloak.KeycloakProperties(
                        "http://keycloak.test",
                        "carenest",
                        "carenest-backend",
                        "",
                        false,
                        java.time.Duration.ofSeconds(3),
                        java.time.Duration.ofSeconds(5)),
                new AuthRefreshCoalescer(clock));
    }

    private static String key() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }

    @Test
    void knownAndUnknownEmailRecoveryHaveTheSamePublicShapeAndNoPlainOtpAtRest() {
        given(state.takeRate(any(), org.mockito.ArgumentMatchers.eq(5), any())).willReturn(true);
        given(userAccounts.findByEmailIgnoreCase("teacher@example.test")).willReturn(List.of(account));
        given(userAccounts.findByEmailIgnoreCase("unknown@example.test")).willReturn(List.of());
        var known = service.forgot(new com.carenest.dto.account.AuthRequests.Forgot("teacher@example.test"));
        var unknown = service.forgot(new com.carenest.dto.account.AuthRequests.Forgot("unknown@example.test"));
        assertThat(known.expiresAt()).isEqualTo(unknown.expiresAt());
        assertThat(known.resendAvailableAt()).isEqualTo(unknown.resendAvailableAt());
        assertThat(known.requestId()).hasSize(43);
        assertThat(unknown.requestId()).hasSize(43).isNotEqualTo(known.requestId());
        var saved = ArgumentCaptor.forClass(com.carenest.entity.account.PasswordToken.class);
        verify(state, org.mockito.Mockito.times(2)).savePasswordToken(saved.capture());
        var sent = ArgumentCaptor.forClass(String.class);
        verify(keycloak).sendOtp(org.mockito.ArgumentMatchers.eq("kc-user-1"), sent.capture());
        assertThat(saved.getAllValues().getFirst().getOtpHmac()).hasSize(64).isNotEqualTo(sent.getValue());
        assertThat(saved.getAllValues().getLast().getUserId()).isNull();
    }

    private Jwt jwt(String value) {
        return Jwt.withTokenValue(value)
                .header("alg", "RS256")
                .subject("kc-user-1")
                .issuedAt(clock.instant())
                .expiresAt(clock.instant().plusSeconds(300))
                .build();
    }

    @Test
    void temporaryPasswordCreatesOnlyABoundChangeHandleWithoutAnAccessTokenOrSession() {
        var proof = mock(AuthGatewayClient.FirstLoginRequired.class);
        when(proof.userId()).thenReturn("kc-user-1");
        when(proof.setupToken()).thenReturn("provider-one-use-proof");
        when(proof.expiresIn()).thenReturn(600L);
        given(keycloak.login("teacher.a", "issued-password")).willThrow(proof);
        given(userAccounts.findByKeycloakUserId("kc-user-1")).willReturn(Optional.of(account));
        var result =
                service.login(new com.carenest.dto.account.AuthRequests.Login("teacher.a", "issued-password", false));
        assertThat(result.cookie()).isNull();
        assertThat(result.data().accessToken()).isNull();
        assertThat(result.data().passwordChangeRequired()).isTrue();
        assertThat(result.data().changeToken()).matches("[A-Za-z0-9_-]{43}");
        var challenge = ArgumentCaptor.forClass(com.carenest.entity.account.PasswordToken.class);
        verify(state).savePasswordToken(challenge.capture());
        assertThat(challenge.getValue().getPurpose()).isEqualTo(PasswordTokenPurpose.FIRST_LOGIN);
    }

    @Test
    void invalidCredentialsNeverCreateAWebSession() {
        given(keycloak.login("missing@example.test", "guess"))
                .willThrow(mock(AuthGatewayClient.InvalidCredentials.class));
        assertThatThrownBy(() -> service.login(
                        new com.carenest.dto.account.AuthRequests.Login("missing@example.test", "guess", false)))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.INVALID_CREDENTIALS.getDefaultDesc());
        verify(state, never()).savePasswordToken(any());
    }

    @Test
    @DisplayName("Đăng nhập Web bằng SĐT gõ kiểu +84/dấu cách vẫn gửi Keycloak đúng tên đăng nhập 10 số")
    void loginNormalizesPhoneIdentifier() {
        given(keycloak.login("0912345678", "guess")).willThrow(mock(AuthGatewayClient.InvalidCredentials.class));

        assertThatThrownBy(() -> service.login(
                        new com.carenest.dto.account.AuthRequests.Login(" +84 912 345 678 ", "guess", false)))
                .hasMessage(ApiCode.INVALID_CREDENTIALS.getDefaultDesc());
        verify(keycloak).login("0912345678", "guess");
    }

    @Test
    void provisionedInactiveAccountCannotAcquireAWebSession() {
        given(keycloak.login("teacher.a", "correct-password"))
                .willReturn(new AuthGatewayClient.Tokens("signed-access", "private-refresh", 300, 36000));
        given(decoder.decode("signed-access")).willReturn(jwt("signed-access"));
        given(currentUsers.getCurrentUser(any(Jwt.class))).willThrow(GlobalException.of(ApiCode.ACCOUNT_INACTIVE));
        assertThatThrownBy(() -> service.login(
                        new com.carenest.dto.account.AuthRequests.Login("teacher.a", "correct-password", false)))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.ACCOUNT_INACTIVE.getDefaultDesc());
    }

    @Test
    void rejectedDecodedLoginRevokesItsPrivateRefreshToken() {
        given(keycloak.login("teacher.a", "secret"))
                .willReturn(new AuthGatewayClient.Tokens("bad-access", "private-refresh", 300, 36000));
        given(decoder.decode("bad-access"))
                .willThrow(new org.springframework.security.oauth2.jwt.JwtException("invalid"));
        assertThatThrownBy(() ->
                        service.login(new com.carenest.dto.account.AuthRequests.Login("teacher.a", "secret", false)))
                .isInstanceOf(GlobalException.class);
        verify(keycloak).logout("private-refresh");
    }

    @Test
    void rejectedCurrentPasswordTokenStillRevokesItsRefreshToken() {
        given(keycloak.login("teacher.a", "secret"))
                .willReturn(new AuthGatewayClient.Tokens("bad-access", "private-refresh", 300, 36000));
        given(decoder.decode("bad-access"))
                .willThrow(new org.springframework.security.oauth2.jwt.JwtException("invalid"));
        assertThatThrownBy(() -> service.changePassword(
                        current,
                        new com.carenest.dto.account.AuthRequests.ChangePassword(
                                "secret", "New-password-123!", "New-password-123!")))
                .isInstanceOf(RuntimeException.class);
        verify(keycloak).logout("private-refresh");
    }

    @Test
    void uncertainPasswordMutationConsumesTheLocalProof() {
        String token = crypto.randomToken();
        String hash = crypto.hash(token);
        var challenge = new AuthStateStore.PasswordTokenState(
                hash,
                91L,
                "kc-user-1",
                null,
                crypto.seal("provider-proof", hash + ":provider"),
                PasswordTokenPurpose.RESET,
                clock.instant().plusSeconds(300),
                clock.instant());
        given(state.claimPassword(org.mockito.ArgumentMatchers.eq(hash), any(), any()))
                .willReturn(new AuthStateStore.Claim<>(AuthStateStore.Status.CLAIMED, challenge));
        org.mockito.Mockito.doThrow(mock(AuthGatewayClient.ProviderUnavailable.class))
                .when(keycloak)
                .changePassword("provider-proof", "New-password-123!", "New-password-123!");
        assertThatThrownBy(() -> service.setPassword(new com.carenest.dto.account.AuthRequests.Reset(
                        token, "New-password-123!", "New-password-123!")))
                .isInstanceOf(GlobalException.class);
        verify(state)
                .finishPassword(
                        org.mockito.ArgumentMatchers.eq(hash), any(), any(), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void inactiveFirstLoginAccountIsDistinguishedFromUnprovisionedAccount() {
        var proof = mock(AuthGatewayClient.FirstLoginRequired.class);
        when(proof.userId()).thenReturn("kc-user-1");
        account.changeStatus(com.carenest.utils.enums.UserStatus.INACTIVE);
        given(keycloak.login("teacher.a", "issued-password")).willThrow(proof);
        given(userAccounts.findByKeycloakUserId("kc-user-1")).willReturn(Optional.of(account));
        assertThatThrownBy(() -> service.login(
                        new com.carenest.dto.account.AuthRequests.Login("teacher.a", "issued-password", false)))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.ACCOUNT_INACTIVE.getDefaultDesc());
        verify(state, never()).savePasswordToken(any());
    }

    @Test
    void resendCannotDeliverBeyondTheSharedEmailRateBudget() {
        String token = crypto.randomToken();
        String hash = crypto.hash(token);
        var challenge = new AuthStateStore.PasswordTokenState(
                hash,
                91L,
                "kc-user-1",
                crypto.keyedHash("email:teacher@example.test"),
                null,
                PasswordTokenPurpose.OTP,
                clock.instant().plusSeconds(300),
                clock.instant());
        given(state.metadata(hash, clock.instant()))
                .willReturn(new AuthStateStore.Claim<>(AuthStateStore.Status.READY, challenge));
        var suppressed = new AuthStateStore.PasswordTokenState(
                hash,
                null,
                null,
                challenge.lookupHash(),
                null,
                challenge.purpose(),
                challenge.expiry(),
                challenge.resendAt());
        given(state.resend(org.mockito.ArgumentMatchers.eq(hash), any(), any(), any(), any()))
                .willReturn(new AuthStateStore.Claim<>(AuthStateStore.Status.READY, suppressed));
        var result = service.resend(token);
        assertThat(result.requestId()).isEqualTo(token);
        verify(keycloak, never()).sendOtp(any(), any());
    }

    private void stubLogin(String refresh) {
        given(keycloak.login("teacher.a", "current-secret"))
                .willReturn(new AuthGatewayClient.Tokens("signed-access", refresh, 300, 1800));
        given(decoder.decode("signed-access")).willReturn(jwt("signed-access"));
        given(currentUsers.getCurrentUser(any(Jwt.class))).willReturn(current);
        given(accounts.getMyAccount(current)).willReturn(profile);
    }

    private String sessionCookie(Instant loginAt, boolean remember, String refresh) {
        return crypto.seal("s1|" + loginAt.getEpochSecond() + "|" + remember + "|" + refresh, "session-cookie");
    }

    private static void assertCode(ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(code.getHttpStatus());
    }

    @Test
    @DisplayName("AUTH-07: login đặt refresh token mã hóa vào cookie, không lưu phiên ở DB")
    void loginIssuesEncryptedCookieWithoutPersistingSession() {
        stubLogin("private-refresh");
        var result = service.login(new AuthRequests.Login("teacher.a", "current-secret", true));
        assertThat(result.data().accessToken()).isEqualTo("signed-access");
        assertThat(result.data().account().roles()).containsExactly(CareNestRole.TEACHER);
        assertThat(result.cookie().value()).doesNotContain("private-refresh").doesNotContain("signed-access");
        assertThat(crypto.open(result.cookie().value(), "session-cookie")).endsWith("|private-refresh");
        assertThat(result.cookie().maxAgeSeconds()).isEqualTo(36000);
        assertThat(result.cookie().value().length()).isLessThan(4096);
        verifyNoInteractions(state);
    }

    @Test
    @DisplayName("AUTH-07: không chọn ghi nhớ thì cookie theo phiên trình duyệt")
    void unrememberedLoginUsesBrowserSessionCookie() {
        stubLogin("private-refresh");
        var result = service.login(new AuthRequests.Login("teacher.a", "current-secret", false));
        assertThat(result.cookie().maxAgeSeconds()).isEqualTo(-1);
    }

    @Test
    @DisplayName("AUTH-07: refresh xoay token vào cookie mới, giữ mốc login và lựa chọn ghi nhớ")
    void refreshRotatesTokenIntoNewCookie() {
        Instant loginAt = clock.instant().minusSeconds(600);
        given(keycloak.refresh("old-refresh"))
                .willReturn(new AuthGatewayClient.Tokens("rotated-access", "rotated-refresh", 300, 1800));
        given(decoder.decode("rotated-access")).willReturn(jwt("rotated-access"));
        given(currentUsers.getCurrentUser(any(Jwt.class))).willReturn(current);
        var result = service.refresh(sessionCookie(loginAt, true, "old-refresh"));
        assertThat(result.data().accessToken()).isEqualTo("rotated-access");
        assertThat(crypto.open(result.cookie().value(), "session-cookie"))
                .isEqualTo("s1|" + loginAt.getEpochSecond() + "|true|rotated-refresh");
        assertThat(result.cookie().maxAgeSeconds()).isEqualTo(36000 - 600);
        verifyNoInteractions(state);
    }

    @Test
    @DisplayName("AUTH-07: cookie bị sửa, sai khóa hoặc sai định dạng trả 401 và không gọi Keycloak")
    void tamperedOrForeignCookieIsRejected() {
        String valid = sessionCookie(clock.instant(), true, "old-refresh");
        String tampered = valid.substring(0, valid.length() - 2) + (valid.endsWith("AA") ? "BB" : "AA");
        byte[] otherKey = new byte[32];
        java.util.Arrays.fill(otherKey, (byte) 1);
        var other = new AuthCrypto(new AuthProperties(
                true,
                Base64.getEncoder().encodeToString(otherKey),
                key(),
                "carenest-auth-gateway",
                "gateway-secret-at-least-32-characters",
                false,
                List.of("http://localhost:5173")));
        String foreign = other.seal("s1|" + clock.instant().getEpochSecond() + "|true|old-refresh", "session-cookie");
        for (String cookie : List.of(tampered, foreign, "not-a-cookie", crypto.seal("garbage", "session-cookie")))
            assertCode(() -> service.refresh(cookie), ApiCode.UNAUTHORIZED);
        assertCode(() -> service.refresh(null), ApiCode.UNAUTHORIZED);
        verify(keycloak, never()).refresh(any());
    }

    @Test
    @DisplayName("AUTH-07: phiên quá 10 giờ kể từ login trả 401 dù Keycloak còn nhận")
    void sessionOlderThanAbsoluteLifetimeIsRejected() {
        String cookie = sessionCookie(clock.instant().minusSeconds(36000), true, "old-refresh");
        assertCode(() -> service.refresh(cookie), ApiCode.UNAUTHORIZED);
        verify(keycloak, never()).refresh(any());
    }

    @Test
    @DisplayName("AUTH-07: Keycloak từ chối refresh token thì trả 401")
    void revokedProviderSessionReturns401() {
        String cookie = sessionCookie(clock.instant(), false, "old-refresh");
        given(keycloak.refresh("old-refresh")).willThrow(mock(AuthGatewayClient.SessionRejected.class));
        assertCode(() -> service.refresh(cookie), ApiCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("AUTH-07: Keycloak tạm lỗi trả 503, retry cùng cookie gọi lại Keycloak")
    void unreachableProviderAllowsRetryWithSameCookie() {
        String cookie = sessionCookie(clock.instant(), false, "old-refresh");
        given(keycloak.refresh("old-refresh"))
                .willThrow(mock(AuthGatewayClient.ProviderUnavailable.class))
                .willReturn(new AuthGatewayClient.Tokens("rotated-access", "rotated-refresh", 300, 1800));
        given(decoder.decode("rotated-access")).willReturn(jwt("rotated-access"));
        given(currentUsers.getCurrentUser(any(Jwt.class))).willReturn(current);
        assertCode(() -> service.refresh(cookie), ApiCode.SERVICE_UNAVAILABLE);
        assertThat(service.refresh(cookie).data().accessToken()).isEqualTo("rotated-access");
        verify(keycloak, times(2)).refresh("old-refresh");
    }

    @Test
    @DisplayName("AUTH-07: tài khoản inactive không refresh được và token vừa xoay bị thu hồi")
    void inactiveAccountCannotRefresh() {
        String cookie = sessionCookie(clock.instant(), true, "old-refresh");
        given(keycloak.refresh("old-refresh"))
                .willReturn(new AuthGatewayClient.Tokens("rotated-access", "rotated-refresh", 300, 1800));
        given(decoder.decode("rotated-access")).willReturn(jwt("rotated-access"));
        given(currentUsers.getCurrentUser(any(Jwt.class))).willThrow(GlobalException.of(ApiCode.ACCOUNT_INACTIVE));
        assertCode(() -> service.refresh(cookie), ApiCode.FORBIDDEN);
        verify(keycloak).logout("rotated-refresh");
    }

    @Test
    @DisplayName("AUTH-21: bếp và phụ huynh không login được Web, không có cookie, token vừa cấp bị thu hồi")
    void appOnlyRolesCannotSignInOnWeb() {
        for (CareNestRole role : List.of(CareNestRole.KITCHEN_STAFF, CareNestRole.PARENT)) {
            org.mockito.Mockito.reset(keycloak, decoder, currentUsers, accounts);
            given(keycloak.login("app.user", "current-secret"))
                    .willReturn(new AuthGatewayClient.Tokens("signed-access", "private-refresh", 300, 1800));
            given(decoder.decode("signed-access")).willReturn(jwt("signed-access"));
            given(currentUsers.getCurrentUser(any(Jwt.class))).willReturn(userWithRoles(role));

            assertThatThrownBy(() -> service.login(new AuthRequests.Login("app.user", "current-secret", true)))
                    .isInstanceOf(GlobalException.class)
                    .hasMessage(ApiCode.FORBIDDEN.getDefaultDesc())
                    .extracting("apiCode")
                    .isEqualTo(ApiCode.FORBIDDEN);
            verify(keycloak).logout("private-refresh");
            verifyNoInteractions(accounts);
        }
    }

    @Test
    @DisplayName("AUTH-21: Admin, HT, PHT, GV login Web được; người có thêm role App vẫn được nếu có role Web")
    void webRolesCanSignInOnWeb() {
        for (CareNestRole role : List.of(
                CareNestRole.ADMIN, CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER)) {
            org.mockito.Mockito.reset(keycloak, decoder, currentUsers, accounts);
            current = userWithRoles(role, CareNestRole.PARENT);
            stubLogin("private-refresh");

            var result = service.login(new AuthRequests.Login("teacher.a", "current-secret", true));

            assertThat(result.cookie()).isNotNull();
            verify(keycloak, never()).logout(any());
        }
    }

    @Test
    @DisplayName("AUTH-21: tài khoản bếp đổi mật khẩu lần đầu trên Web bị từ chối, không tạo mã đổi mật khẩu")
    void appOnlyFirstLoginIsRejectedOnWeb() throws Exception {
        var proof = mock(AuthGatewayClient.FirstLoginRequired.class);
        when(proof.userId()).thenReturn("kc-kitchen");
        UserAccount kitchen = UserAccount.create(
                "kc-kitchen", "kitchen.a", "kitchen@example.test", "Bếp Demo", CareNestRole.KITCHEN_STAFF);
        given(keycloak.login("kitchen.a", "issued-password")).willThrow(proof);
        given(userAccounts.findByKeycloakUserId("kc-kitchen")).willReturn(Optional.of(kitchen));

        assertCode(
                () -> service.login(new AuthRequests.Login("kitchen.a", "issued-password", false)), ApiCode.FORBIDDEN);
        verify(state, never()).savePasswordToken(any());
    }

    @Test
    @DisplayName(
            "AUTH-21: phiên Web của tài khoản không còn role Web thì refresh bị từ chối, token vừa xoay bị thu hồi")
    void refreshRejectsAccountWithoutWebRole() {
        String cookie = sessionCookie(clock.instant(), true, "old-refresh");
        given(keycloak.refresh("old-refresh"))
                .willReturn(new AuthGatewayClient.Tokens("rotated-access", "rotated-refresh", 300, 1800));
        given(decoder.decode("rotated-access")).willReturn(jwt("rotated-access"));
        given(currentUsers.getCurrentUser(any(Jwt.class))).willReturn(userWithRoles(CareNestRole.KITCHEN_STAFF));

        assertCode(() -> service.refresh(cookie), ApiCode.FORBIDDEN);
        verify(keycloak).logout("rotated-refresh");
    }

    @Test
    @DisplayName(
            "AUTH-21: tài khoản cũ chưa có primary_role vẫn được đổi mật khẩu lần đầu; lần login sau mới kiểm role")
    void firstLoginWithoutPrimaryRoleStillIssuesChallenge() {
        var proof = mock(AuthGatewayClient.FirstLoginRequired.class);
        when(proof.userId()).thenReturn("kc-user-1");
        when(proof.setupToken()).thenReturn("provider-one-use-proof");
        when(proof.expiresIn()).thenReturn(600L);
        given(keycloak.login("teacher.a", "issued-password")).willThrow(proof);
        given(userAccounts.findByKeycloakUserId("kc-user-1")).willReturn(Optional.of(account));

        var result = service.login(new AuthRequests.Login("teacher.a", "issued-password", false));

        assertThat(account.getPrimaryRole()).isNull();
        assertThat(result.data().passwordChangeRequired()).isTrue();
        verify(state).savePasswordToken(any());
    }

    @Test
    @DisplayName("AUTH-21: quên mật khẩu Web với email bếp/phụ huynh trả như email không tồn tại, không gửi OTP")
    void appOnlyRecoveryLooksLikeUnknownEmailAndSendsNoOtp() {
        given(state.takeRate(any(), org.mockito.ArgumentMatchers.eq(5), any())).willReturn(true);
        UserAccount kitchen = UserAccount.create(
                "kc-kitchen", "kitchen.a", "kitchen@example.test", "Bếp Demo", CareNestRole.KITCHEN_STAFF);
        UserAccount parent = UserAccount.create(
                "kc-parent", "0900000001", "parent@example.test", "Phụ huynh Demo", CareNestRole.PARENT);
        given(userAccounts.findByEmailIgnoreCase("kitchen@example.test")).willReturn(List.of(kitchen));
        given(userAccounts.findByEmailIgnoreCase("parent@example.test")).willReturn(List.of(parent));
        given(userAccounts.findByEmailIgnoreCase("unknown@example.test")).willReturn(List.of());

        var unknown = service.forgot(new AuthRequests.Forgot("unknown@example.test"));
        var kitchenReply = service.forgot(new AuthRequests.Forgot("kitchen@example.test"));
        var parentReply = service.forgot(new AuthRequests.Forgot("parent@example.test"));

        assertThat(kitchenReply.expiresAt()).isEqualTo(unknown.expiresAt());
        assertThat(parentReply.resendAvailableAt()).isEqualTo(unknown.resendAvailableAt());
        var saved = ArgumentCaptor.forClass(com.carenest.entity.account.PasswordToken.class);
        verify(state, org.mockito.Mockito.times(3)).savePasswordToken(saved.capture());
        assertThat(saved.getAllValues())
                .allSatisfy(token -> assertThat(token.getUserId()).isNull());
        verify(keycloak, never()).sendOtp(any(), any());
    }

    private static CurrentUser userWithRoles(CareNestRole... roles) {
        return new CurrentUser(92L, "kc-user-2", "app.user", "Tài khoản Demo", null, Set.of(roles));
    }

    @Test
    @DisplayName("AUTH-07: logout thu hồi refresh token ở Keycloak; cookie hỏng không lỗi")
    void logoutRevokesProviderTokenAndIgnoresBrokenCookie() {
        service.logout(sessionCookie(clock.instant(), true, "old-refresh"));
        verify(keycloak).logout("old-refresh");
        service.logout("not-a-cookie");
        service.logout(null);
        verifyNoMoreInteractions(keycloak);
    }
}
