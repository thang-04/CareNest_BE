package com.carenest.service.account.impl;

import com.carenest.config.AuthProperties;
import com.carenest.dto.account.AuthRequests;
import com.carenest.dto.account.AuthResponses;
import com.carenest.dto.account.MyAccountResponse;
import com.carenest.entity.account.PasswordToken;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.AuthGatewayClient;
import com.carenest.integration.keycloak.KeycloakProperties;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.AuthCrypto;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.service.account.AccountService;
import com.carenest.service.account.AuthService;
import com.carenest.service.account.AuthService.SessionCookie;
import com.carenest.service.account.AuthStateStore;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.Auth;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.PasswordTokenPurpose;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    private static final String LOG_PREFIX = "[AuthServiceImpl]";
    private static final String COOKIE_CONTEXT = "session-cookie";
    private static final String COOKIE_VERSION = "s1";
    private static final int MAX_COOKIE_LENGTH = 4096;
    // AUTH-21: bếp và phụ huynh chỉ dùng App, gateway Web không cấp phiên cho họ
    private static final Set<CareNestRole> WEB_ROLES = Collections.unmodifiableSet(
            EnumSet.of(CareNestRole.ADMIN, CareNestRole.PRINCIPAL, CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER));

    private final AuthProperties properties;
    private final AuthCrypto crypto;
    private final AuthGatewayClient keycloak;
    private final UserAccountRepository accounts;
    private final AuthStateStore state;
    private final JwtDecoder jwtDecoder;
    private final CurrentUserService currentUsers;
    private final AccountService accountService;
    private final Clock clock;
    private final KeycloakProperties identity;
    private final AuthRefreshCoalescer refreshes;

    public AuthServiceImpl(
            AuthProperties properties,
            AuthCrypto crypto,
            AuthGatewayClient keycloak,
            UserAccountRepository accounts,
            AuthStateStore state,
            JwtDecoder jwtDecoder,
            CurrentUserService currentUsers,
            AccountService accountService,
            Clock clock,
            KeycloakProperties identity,
            AuthRefreshCoalescer refreshes) {
        this.properties = properties;
        this.crypto = crypto;
        this.keycloak = keycloak;
        this.accounts = accounts;
        this.state = state;
        this.jwtDecoder = jwtDecoder;
        this.currentUsers = currentUsers;
        this.accountService = accountService;
        this.clock = clock;
        this.identity = identity;
        this.refreshes = refreshes;
    }

    @Override
    public AuthResponses.Csrf csrf(String token) {
        String logPrefix = LOG_PREFIX + "|csrf";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            if (token == null || token.isBlank()) throw GlobalException.of(ApiCode.CSRF_TOKEN_UNAVAILABLE);
            log.info("{}|END", logPrefix);
            return new AuthResponses.Csrf(token, "X-XSRF-TOKEN");
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public LoginResult login(AuthRequests.Login request) {
        String logPrefix = LOG_PREFIX + "|login";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            if (request == null) throw malformed();
            AuthGatewayClient.Tokens tokens = null;
            boolean issued = false;
            try {
                tokens = keycloak.login(request.identifier().trim(), request.password());
                Jwt jwt = decode(tokens.accessToken());
                CurrentUser current = currentUsers.getCurrentUser(jwt);
                ensureWebRole(current.roles());
                MyAccountResponse profile = accountService.getMyAccount(current);
                if (tokens.refreshToken() == null) throw invalidSession();
                Instant now = clock.instant();
                SessionCookie cookie = sessionCookie(tokens.refreshToken(), request.remember(), now, now);
                issued = true;
                log.info("{}|END", logPrefix);
                return new LoginResult(
                        new AuthResponses.LoginData(
                                tokens.accessToken(), now.plusSeconds(tokens.expiresIn()), false, null, profile),
                        cookie);
            } catch (AuthGatewayClient.FirstLoginRequired challenge) {
                UserAccount account = accounts.findByKeycloakUserId(challenge.userId())
                        .orElseThrow(() -> GlobalException.of(ApiCode.ACCOUNT_NOT_PROVISIONED));
                if (!account.isActive()) throw GlobalException.of(ApiCode.ACCOUNT_INACTIVE);
                // Chưa có token nên dựa vào primary_role; tài khoản cũ chưa có primary_role để lần login sau kiểm
                if (account.getPrimaryRole() != null) ensureWebRole(Set.of(account.getPrimaryRole()));
                String handle = crypto.randomToken();
                String hash = crypto.hash(handle);
                Instant now = clock.instant();
                long ttl = Math.min(Auth.SETUP_TOKEN_SECONDS, challenge.expiresIn());
                state.savePasswordToken(PasswordToken.create(
                        hash,
                        PasswordTokenPurpose.FIRST_LOGIN,
                        account.getId(),
                        account.getKeycloakUserId(),
                        null,
                        crypto.seal(challenge.setupToken(), hash + ":provider"),
                        null,
                        now,
                        now.plusSeconds(ttl),
                        now));
                log.info("{}|END", logPrefix);
                return new LoginResult(
                        new AuthResponses.LoginData(null, now.plusSeconds(ttl), true, handle, null), null);
            } catch (AuthGatewayClient.InvalidCredentials ex) {
                throw GlobalException.of(ApiCode.INVALID_CREDENTIALS);
            } catch (AuthGatewayClient.ProviderUnavailable ex) {
                throw GlobalException.of(ApiCode.AUTH_SERVICE_UNAVAILABLE);
            } catch (org.springframework.security.oauth2.jwt.JwtException ex) {
                throw GlobalException.of(ApiCode.AUTH_SERVICE_UNAVAILABLE);
            } finally {
                if (!issued && tokens != null && tokens.refreshToken() != null) keycloak.logout(tokens.refreshToken());
            }
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public RefreshResult refresh(String cookie) {
        String logPrefix = LOG_PREFIX + "|refresh";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            if (cookie == null || cookie.isBlank() || cookie.length() > MAX_COOKIE_LENGTH) throw invalidSession();
            RefreshResult result = refreshes.run(crypto.hash(cookie), () -> rotate(openSession(cookie)));
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Idle 30' và trần 10h do Keycloak áp; BE chỉ chặn thêm trần 10h tính từ lúc login ghi trong cookie.
    private RefreshResult rotate(SessionPayload session) {
        Instant now = clock.instant();
        if (!now.isBefore(session.loginAt().plusSeconds(Auth.SESSION_SECONDS))) throw invalidSession();
        AuthGatewayClient.Tokens tokens;
        try {
            tokens = keycloak.refresh(session.refreshToken());
        } catch (AuthGatewayClient.ProviderUnavailable ex) {
            // Giữ cookie để client retry; nếu Keycloak đã rotate, token cũ bị Keycloak từ chối (revokeRefreshToken).
            throw GlobalException.of(ApiCode.AUTH_SERVICE_UNAVAILABLE);
        } catch (AuthGatewayClient.SessionRejected ex) {
            throw invalidSession();
        }
        boolean issued = false;
        try {
            if (tokens.refreshToken() == null) throw invalidSession();
            ensureWebRole(
                    currentUsers.getCurrentUser(decode(tokens.accessToken())).roles());
            SessionCookie cookie = sessionCookie(tokens.refreshToken(), session.remember(), session.loginAt(), now);
            issued = true;
            return new RefreshResult(
                    new AuthResponses.AccessToken(tokens.accessToken(), now.plusSeconds(tokens.expiresIn())), cookie);
        } catch (org.springframework.security.oauth2.jwt.JwtException ex) {
            throw GlobalException.of(ApiCode.AUTH_SERVICE_UNAVAILABLE);
        } finally {
            // Tài khoản inactive/chưa provision: thu hồi token vừa xoay để không còn phiên treo ở Keycloak.
            if (!issued && tokens.refreshToken() != null) keycloak.logout(tokens.refreshToken());
        }
    }

    @Override
    public void logout(String cookie) {
        String logPrefix = LOG_PREFIX + "|logout";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            if (cookie == null || cookie.isBlank() || cookie.length() > MAX_COOKIE_LENGTH) {
                log.info("{}|END", logPrefix);
                return;
            }
            SessionPayload session;
            try {
                session = openSession(cookie);
            } catch (GlobalException ex) {
                log.info("{}|END", logPrefix);
                return;
            }
            keycloak.logout(session.refreshToken());
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private SessionCookie sessionCookie(String refreshToken, boolean remember, Instant loginAt, Instant now) {
        String payload = COOKIE_VERSION + "|" + loginAt.getEpochSecond() + "|" + remember + "|" + refreshToken;
        long remaining = loginAt.plusSeconds(Auth.SESSION_SECONDS).getEpochSecond() - now.getEpochSecond();
        return new SessionCookie(crypto.seal(payload, COOKIE_CONTEXT), remember ? Math.max(0, remaining) : -1);
    }

    // Cookie bị sửa, sai khóa hoặc sai định dạng đều coi là phiên không hợp lệ (401), không phải lỗi hệ thống.
    private SessionPayload openSession(String cookie) {
        String payload;
        try {
            payload = crypto.open(cookie, COOKIE_CONTEXT);
        } catch (GlobalException ex) {
            throw invalidSession();
        }
        String[] parts = payload.split("\\|", 4);
        if (parts.length != 4 || !COOKIE_VERSION.equals(parts[0]) || parts[3].isEmpty()) throw invalidSession();
        try {
            return new SessionPayload(
                    Instant.ofEpochSecond(Long.parseLong(parts[1])), Boolean.parseBoolean(parts[2]), parts[3]);
        } catch (NumberFormatException ex) {
            throw invalidSession();
        }
    }

    private record SessionPayload(Instant loginAt, boolean remember, String refreshToken) {
        @Override
        public String toString() {
            return "SessionPayload[redacted]";
        }
    }

    @Override
    public AuthResponses.OtpRequest forgot(AuthRequests.Forgot request) {
        String logPrefix = LOG_PREFIX + "|forgot";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            if (request == null) throw malformed();
            Instant now = clock.instant();
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            String lookup = crypto.keyedHash("email:" + email);
            long window = now.getEpochSecond() / 1800;
            boolean allowed = state.takeRate(
                    crypto.keyedHash("recovery-rate:" + lookup + ":" + window),
                    Auth.RATE_LIMIT,
                    Instant.ofEpochSecond((window + 1) * 1800));
            // AUTH-21: bếp/phụ huynh không khôi phục mật khẩu qua Web; coi như email không tồn tại để không lộ loại TK
            List<UserAccount> matches = accounts.findByEmailIgnoreCase(email).stream()
                    .filter(UserAccount::isActive)
                    .filter(AuthServiceImpl::mayUseWeb)
                    .toList();
            UserAccount account = matches.size() == 1 && allowed ? matches.getFirst() : null;
            String requestId = crypto.randomToken();
            String hash = crypto.hash(requestId);
            String code = crypto.randomOtp();
            state.savePasswordToken(PasswordToken.create(
                    hash,
                    PasswordTokenPurpose.OTP,
                    account == null ? null : account.getId(),
                    account == null ? null : account.getKeycloakUserId(),
                    lookup,
                    null,
                    crypto.otpDigest(requestId, code),
                    now,
                    now.plusSeconds(Auth.OTP_SECONDS),
                    now.plusSeconds(Auth.OTP_RESEND_SECONDS)));
            if (account != null) sendOtpQuietly(account.getKeycloakUserId(), code);
            log.info("{}|END", logPrefix);
            return new AuthResponses.OtpRequest(
                    requestId, now.plusSeconds(Auth.OTP_SECONDS), now.plusSeconds(Auth.OTP_RESEND_SECONDS));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AuthResponses.OtpRequest otpMetadata(String requestId) {
        String logPrefix = LOG_PREFIX + "|otpMetadata";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            String hash = crypto.hash(validOpaque(requestId));
            var result = state.metadata(hash, clock.instant());
            if (result.status() != AuthStateStore.Status.READY) throw notFound();
            log.info("{}|END", logPrefix);
            return otpResponse(requestId, result.data());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AuthResponses.OtpRequest resend(String requestId) {
        String logPrefix = LOG_PREFIX + "|resend";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            String raw = validOpaque(requestId);
            Instant now = clock.instant();
            var pending = state.metadata(crypto.hash(raw), now);
            if (pending.status() != AuthStateStore.Status.READY) throw notFound();
            String code = crypto.randomOtp();
            long window = now.getEpochSecond() / 1800;
            var changed = state.resend(
                    crypto.hash(raw),
                    crypto.otpDigest(raw, code),
                    now,
                    crypto.keyedHash("recovery-rate:" + pending.data().lookupHash() + ":" + window),
                    Instant.ofEpochSecond((window + 1) * 1800));
            if (changed.status() == AuthStateStore.Status.THROTTLED)
                throw GlobalException.of(ApiCode.OTP_RESEND_TOO_SOON);
            if (changed.status() != AuthStateStore.Status.READY) throw notFound();
            if (changed.data().userId() != null) sendOtpQuietly(changed.data().subject(), code);
            log.info("{}|END", logPrefix);
            return otpResponse(raw, changed.data());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AuthResponses.ResetToken verifyOtp(AuthRequests.VerifyOtp requestIdAndOtp, String requestId) {
        String logPrefix = LOG_PREFIX + "|verifyOtp";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            if (requestIdAndOtp == null) throw malformed();
            String raw = validOpaque(requestId);
            var result =
                    state.verifyOtp(crypto.hash(raw), crypto.otpDigest(raw, requestIdAndOtp.otp()), clock.instant());
            if (result.status() == AuthStateStore.Status.THROTTLED)
                throw GlobalException.of(ApiCode.RECOVERY_REQUEST_UNAVAILABLE);
            if (result.status() != AuthStateStore.Status.READY || result.data().userId() == null)
                throw GlobalException.of(ApiCode.OTP_INVALID_OR_EXPIRED);
            var otp = result.data();
            Instant now = clock.instant();
            String handle = crypto.randomToken();
            String hash = crypto.hash(handle);
            String assertion = crypto.assertion(otp.subject(), identity.realm(), "RECOVERY_VERIFIED", now);
            try {
                String providerToken = keycloak.verifiedCapability(assertion);
                state.savePasswordToken(PasswordToken.create(
                        hash,
                        PasswordTokenPurpose.RESET,
                        otp.userId(),
                        otp.subject(),
                        null,
                        crypto.seal(providerToken, hash + ":provider"),
                        null,
                        now,
                        now.plusSeconds(Auth.OTP_SECONDS * 2),
                        now));
            } catch (AuthGatewayClient.ProviderUnavailable ex) {
                throw GlobalException.of(ApiCode.AUTH_SERVICE_UNAVAILABLE);
            }
            log.info("{}|END", logPrefix);
            return new AuthResponses.ResetToken(handle, now.plusSeconds(Auth.OTP_SECONDS * 2));
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void setPassword(AuthRequests.Reset request) {
        String logPrefix = LOG_PREFIX + "|setPassword";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            if (request == null || !request.newPassword().equals(request.confirmPassword())) throw malformed();
            String token = validOpaque(request.token());
            String hash = crypto.hash(token);
            String owner = crypto.randomToken();
            Instant now = clock.instant();
            var claim = state.claimPassword(hash, owner, now);
            if (claim.status() != AuthStateStore.Status.CLAIMED) throw notFound();
            try {
                keycloak.changePassword(
                        claim.data().providerCipher() == null
                                ? null
                                : crypto.open(claim.data().providerCipher(), hash + ":provider"),
                        request.newPassword(),
                        request.confirmPassword());
                state.finishPassword(hash, owner, clock.instant(), true);
                if (claim.data().userId() != null)
                    state.revokeAccountAuth(claim.data().userId());
            } catch (AuthGatewayClient.PasswordRejected ex) {
                state.finishPassword(hash, owner, clock.instant(), false);
                throw GlobalException.of(ApiCode.PASSWORD_POLICY_VIOLATION);
            } catch (AuthGatewayClient.CapabilityRejected ex) {
                state.finishPassword(hash, owner, clock.instant(), true);
                throw notFound();
            } catch (AuthGatewayClient.ProviderUnavailable ex) {
                state.finishPassword(hash, owner, clock.instant(), true);
                throw GlobalException.of(ApiCode.AUTH_SERVICE_UNAVAILABLE);
            } catch (RuntimeException ex) {
                state.finishPassword(hash, owner, clock.instant(), true);
                throw ex;
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void changePassword(CurrentUser current, AuthRequests.ChangePassword request) {
        String logPrefix = LOG_PREFIX + "|changePassword|userId=" + (current == null ? null : current.userId());
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            if (current == null || request == null || !request.newPassword().equals(request.confirmPassword()))
                throw malformed();
            AuthGatewayClient.Tokens verified = null;
            try {
                verified = keycloak.login(current.username(), request.currentPassword());
                Jwt jwt = decode(verified.accessToken());
                CurrentUser checked = currentUsers.getCurrentUser(jwt);
                if (!current.keycloakUserId().equals(checked.keycloakUserId())) throw invalidCredentials();
                Instant now = clock.instant();
                String assertion =
                        crypto.assertion(current.keycloakUserId(), identity.realm(), "CURRENT_PASSWORD_VERIFIED", now);
                String proof = keycloak.verifiedCapability(assertion);
                keycloak.changePassword(proof, request.newPassword(), request.confirmPassword());
                state.revokeAccountAuth(current.userId());
            } catch (AuthGatewayClient.InvalidCredentials | AuthGatewayClient.FirstLoginRequired ex) {
                throw invalidCredentials();
            } catch (org.springframework.security.oauth2.jwt.JwtException ex) {
                throw GlobalException.of(ApiCode.AUTH_SERVICE_UNAVAILABLE);
            } catch (AuthGatewayClient.PasswordRejected ex) {
                throw GlobalException.of(ApiCode.PASSWORD_POLICY_VIOLATION);
            } catch (AuthGatewayClient.CapabilityRejected ex) {
                throw GlobalException.of(ApiCode.INVALID_CREDENTIALS);
            } catch (AuthGatewayClient.ProviderUnavailable ex) {
                throw GlobalException.of(ApiCode.AUTH_SERVICE_UNAVAILABLE);
            } finally {
                // This direct-grant result proves the old password only; tokens stay server-side and are immediately
                // revoked.
                if (verified != null && verified.refreshToken() != null) keycloak.logout(verified.refreshToken());
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private void sendOtpQuietly(String subject, String code) {
        try {
            keycloak.sendOtp(subject, code);
        } catch (AuthGatewayClient.ProviderUnavailable ex) {
            /* Public recovery remains generic and resend is available. */
        }
    }

    private Jwt decode(String raw) {
        return jwtDecoder.decode(raw);
    }

    private static String validOpaque(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidSession();
        return token;
    }

    private static AuthResponses.OtpRequest otpResponse(String requestId, AuthStateStore.PasswordTokenState state) {
        return new AuthResponses.OtpRequest(requestId, state.expiry(), state.resendAt());
    }

    private void ensureEnabled() {
        if (!properties.enabled()) throw GlobalException.of(ApiCode.WEB_AUTH_GATEWAY_DISABLED);
    }

    private static void ensureWebRole(Set<CareNestRole> roles) {
        if (Collections.disjoint(roles, WEB_ROLES)) throw GlobalException.of(ApiCode.FORBIDDEN);
    }

    // Tài khoản cũ chưa có primary_role cho qua, như nhánh đổi mật khẩu lần đầu
    private static boolean mayUseWeb(UserAccount account) {
        return account.getPrimaryRole() == null || WEB_ROLES.contains(account.getPrimaryRole());
    }

    private static GlobalException malformed() {
        return GlobalException.of(ApiCode.INVALID_AUTH_REQUEST);
    }

    private static GlobalException notFound() {
        return GlobalException.of(ApiCode.AUTH_REQUEST_NOT_FOUND);
    }

    private static GlobalException invalidSession() {
        return GlobalException.of(ApiCode.WEB_SESSION_INVALID);
    }

    private static GlobalException invalidCredentials() {
        return GlobalException.of(ApiCode.INVALID_CREDENTIALS);
    }
}
