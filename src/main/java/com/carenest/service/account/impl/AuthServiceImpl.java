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
import com.carenest.utils.PhoneNumbers;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ClientPlatform;
import com.carenest.utils.enums.PasswordTokenPurpose;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
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
    private static final String TAKEOVER_CONTEXT = "takeover-cookie";
    private static final String TAKEOVER_VERSION = "t1";
    private static final int MAX_COOKIE_LENGTH = 4096;
    // AUTH-21: bếp và phụ huynh chỉ dùng App, gateway Web không cấp phiên cho họ
    private static final Set<CareNestRole> WEB_ROLES = ClientPlatform.WEB.allowedRoles();

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
    private final WebSessionTakeoverStore takeovers;

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
            AuthRefreshCoalescer refreshes,
            WebSessionTakeoverStore takeovers) {
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
        this.takeovers = takeovers;
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
            // Gõ SĐT kiểu nào (+84, dấu cách) cũng về đúng tên đăng nhập; email/tên cũ giữ nguyên
            String typed = request.identifier().trim();
            String username = PhoneNumbers.normalize(typed).orElse(typed);
            try {
                tokens = keycloak.login(username, request.password());
                Jwt jwt = decode(tokens.accessToken());
                CurrentUser current = currentUsers.getCurrentUser(jwt);
                ensureWebRole(current.roles());
                MyAccountResponse profile = accountService.getMyAccount(current);
                if (tokens.refreshToken() == null) throw invalidSession();
                Instant now = clock.instant();
                // AUTH-28: còn phiên Web khác ⇒ chưa cấp phiên, chưa đá ai; chờ người dùng chọn dùng ở đâu
                String sessionId = jwt.getClaimAsString("sid");
                if (!properties.singleWebSession()) {
                    log.info("{}|sessionLimitSkipped|reason=disabled", logPrefix);
                } else if (sessionId == null || sessionId.isBlank()) {
                    log.warn("{}|sessionLimitSkipped|reason=sidMissing", logPrefix);
                } else if (keycloak.hasOtherWebSessions(jwt.getSubject(), sessionId)) {
                    LoginResult pending = startTakeover(jwt.getSubject(), sessionId, request.remember(), tokens, now);
                    issued = true;
                    log.info("{}|takeoverPending|END", logPrefix);
                    return pending;
                }
                SessionCookie cookie = sessionCookie(tokens.refreshToken(), request.remember(), now, now);
                issued = true;
                log.info("{}|END", logPrefix);
                return new LoginResult(
                        new AuthResponses.LoginData(
                                tokens.accessToken(), now.plusSeconds(tokens.expiresIn()), false, null, profile, null),
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
                        new AuthResponses.LoginData(null, now.plusSeconds(ttl), true, handle, null, null), null);
            } catch (AuthGatewayClient.AccountDisabled ex) {
                throw GlobalException.of(ApiCode.ACCOUNT_INACTIVE);
            } catch (AuthGatewayClient.InvalidCredentials ex) {
                throw loginRejected(username);
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

    // Refresh token của lượt chờ nằm trong cookie niêm phong (security.md: server không lưu refresh token)
    private LoginResult startTakeover(
            String userId, String sessionId, boolean remember, AuthGatewayClient.Tokens tokens, Instant now) {
        String id = crypto.randomToken();
        Instant expiresAt = now.plusSeconds(Auth.TAKEOVER_SECONDS);
        takeovers.create(id, userId, sessionId, now, expiresAt);
        String payload = TAKEOVER_VERSION + "|" + id + "|" + remember + "|" + tokens.refreshToken();
        SessionCookie cookie = new SessionCookie(crypto.seal(payload, TAKEOVER_CONTEXT), Auth.TAKEOVER_SECONDS);
        return new LoginResult(
                new AuthResponses.LoginData(null, expiresAt, false, null, null, new AuthResponses.Takeover(expiresAt)),
                cookie);
    }

    @Override
    public LoginResult confirmTakeover(String takeoverCookie) {
        String logPrefix = LOG_PREFIX + "|confirmTakeover";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            TakeoverPayload pending = openTakeover(takeoverCookie);
            Instant now = clock.instant();
            // take() xóa lượt chờ nguyên tử: xác nhận ở đây và từ chối ở phiên kia không cùng thắng
            WebSessionTakeoverStore.Takeover takeover =
                    takeovers.take(pending.id()).orElse(null);
            if (takeover != null && takeover.rejected())
                throw GlobalException.of(ApiCode.WEB_SESSION_TAKEOVER_REJECTED);
            if (takeover == null || !now.isBefore(takeover.expiresAt())) {
                keycloak.logout(pending.refreshToken());
                throw takeoverExpired();
            }
            AuthGatewayClient.Tokens tokens;
            try {
                tokens = keycloak.refresh(pending.refreshToken());
            } catch (AuthGatewayClient.SessionRejected ex) {
                throw takeoverExpired();
            } catch (AuthGatewayClient.ProviderUnavailable ex) {
                throw GlobalException.of(ApiCode.AUTH_SERVICE_UNAVAILABLE);
            }
            boolean issued = false;
            try {
                if (tokens.refreshToken() == null) throw takeoverExpired();
                Jwt jwt = decode(tokens.accessToken());
                if (!takeover.userId().equals(jwt.getSubject())) throw takeoverExpired();
                CurrentUser current = currentUsers.getCurrentUser(jwt);
                ensureWebRole(current.roles());
                MyAccountResponse profile = accountService.getMyAccount(current);
                // AUTH-28: người dùng đã chọn dùng ở đây ⇒ giờ mới hủy phiên Web khác (chặn ngay token còn hạn)
                state.revokeWebSessions(
                        takeover.userId(), keycloak.revokeOtherWebSessions(takeover.userId(), takeover.sessionId()));
                takeovers.dropOthers(takeover.userId(), takeover.id());
                SessionCookie cookie = sessionCookie(tokens.refreshToken(), pending.remember(), now, now);
                issued = true;
                log.info("{}|END", logPrefix);
                return new LoginResult(
                        new AuthResponses.LoginData(
                                tokens.accessToken(), now.plusSeconds(tokens.expiresIn()), false, null, profile, null),
                        cookie);
            } catch (org.springframework.security.oauth2.jwt.JwtException ex) {
                throw GlobalException.of(ApiCode.AUTH_SERVICE_UNAVAILABLE);
            } finally {
                if (!issued && tokens.refreshToken() != null) keycloak.logout(tokens.refreshToken());
            }
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void cancelTakeover(String takeoverCookie) {
        String logPrefix = LOG_PREFIX + "|cancelTakeover";
        log.info("{}|START", logPrefix);
        try {
            ensureEnabled();
            TakeoverPayload pending;
            try {
                pending = openTakeover(takeoverCookie);
            } catch (GlobalException ex) {
                log.info("{}|END", logPrefix);
                return;
            }
            takeovers.take(pending.id());
            keycloak.logout(pending.refreshToken());
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AuthResponses.SessionStatus sessionStatus(CurrentUser current) {
        String logPrefix = LOG_PREFIX + "|sessionStatus|userId=" + current.userId();
        log.info("{}|START", logPrefix);
        try {
            AuthResponses.PendingTakeover pending = takeovers
                    .pendingFor(current.keycloakUserId(), clock.instant())
                    .map(t -> new AuthResponses.PendingTakeover(t.id(), t.requestedAt(), t.expiresAt()))
                    .orElse(null);
            log.info("{}|END", logPrefix);
            return new AuthResponses.SessionStatus(pending);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void rejectTakeover(CurrentUser current, String takeoverId) {
        String logPrefix = LOG_PREFIX + "|rejectTakeover|userId=" + current.userId();
        log.info("{}|START", logPrefix);
        try {
            // Chỉ từ chối được lượt chờ của chính tài khoản mình (không tin id client gửi)
            WebSessionTakeoverStore.Takeover rejected = takeovers
                    .reject(takeoverId, current.keycloakUserId(), clock.instant())
                    .orElseThrow(AuthServiceImpl::takeoverExpired);
            keycloak.revokeSession(rejected.sessionId());
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void expireTakeovers() {
        String logPrefix = LOG_PREFIX + "|expireTakeovers";
        try {
            for (WebSessionTakeoverStore.Takeover expired : takeovers.drainExpired(clock.instant())) {
                // Lượt bị từ chối đã hủy phiên lúc từ chối
                if (!expired.rejected()) keycloak.revokeSession(expired.sessionId());
            }
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private TakeoverPayload openTakeover(String cookie) {
        if (cookie == null || cookie.isBlank() || cookie.length() > MAX_COOKIE_LENGTH) throw takeoverExpired();
        String payload;
        try {
            payload = crypto.open(cookie, TAKEOVER_CONTEXT);
        } catch (GlobalException ex) {
            throw takeoverExpired();
        }
        String[] parts = payload.split("\\|", 4);
        if (parts.length != 4 || !TAKEOVER_VERSION.equals(parts[0]) || parts[3].isEmpty()) throw takeoverExpired();
        return new TakeoverPayload(parts[1], Boolean.parseBoolean(parts[2]), parts[3]);
    }

    private static GlobalException takeoverExpired() {
        return GlobalException.of(ApiCode.WEB_SESSION_TAKEOVER_EXPIRED);
    }

    private record TakeoverPayload(String id, boolean remember, String refreshToken) {
        @Override
        public String toString() {
            return "TakeoverPayload[redacted]";
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
            // AUTH-28: bị đăng nhập Web mới hơn thay thế thì báo đúng lý do, FE không nói nhầm là hết hạn
            if (keycloak.replacedByNewerWebSession(session.refreshToken(), session.loginAt()))
                throw GlobalException.of(ApiCode.WEB_SESSION_REPLACED);
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
            return AuthResponses.OtpRequest.of(
                    requestId, now.plusSeconds(Auth.OTP_SECONDS), now.plusSeconds(Auth.OTP_RESEND_SECONDS), now);
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
            return otpResponse(requestId, result.data(), clock.instant());
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
            return otpResponse(raw, changed.data(), now);
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
                if (claim.data().userId() != null) {
                    accounts.findById(claim.data().userId())
                            .ifPresent(account ->
                                    PasswordRules.rejectUsername(request.newPassword(), account.getUsername()));
                }
            } catch (GlobalException ex) {
                // Token còn dùng lại được để nhập mật khẩu khác
                state.finishPassword(hash, owner, clock.instant(), false);
                throw ex;
            }
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
            PasswordRules.rejectUsername(request.newPassword(), current.username());
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
            } catch (AuthGatewayClient.AccountDisabled ex) {
                throw GlobalException.of(ApiCode.ACCOUNT_INACTIVE);
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

    // Mã yêu cầu sai dạng cũng là "không tồn tại" (404), không phải phiên đăng nhập hỏng
    private static String validOpaque(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw notFound();
        return token;
    }

    private static AuthResponses.OtpRequest otpResponse(
            String requestId, AuthStateStore.PasswordTokenState state, Instant now) {
        return AuthResponses.OtpRequest.of(requestId, state.expiry(), state.resendAt(), now);
    }

    private void ensureEnabled() {
        if (!properties.enabled()) throw GlobalException.of(ApiCode.WEB_AUTH_GATEWAY_DISABLED);
    }

    // AUTH-03 (SRS): Keycloak báo đang khóa tạm giống sai mật khẩu ⇒ hỏi lại để nói rõ thời gian chờ
    private GlobalException loginRejected(String username) {
        Instant now = clock.instant();
        return keycloak.loginBlockedUntil(username)
                .filter(until -> until.isAfter(now))
                .map(until -> {
                    long seconds =
                            Math.max(1, java.time.Duration.between(now, until).toSeconds());
                    return GlobalException.withData(
                            ApiCode.LOGIN_TEMPORARILY_LOCKED,
                            java.util.Map.of("retryAfterSeconds", seconds, "retryAt", until),
                            waitText(seconds));
                })
                .orElseGet(() -> GlobalException.of(ApiCode.INVALID_CREDENTIALS));
    }

    private static String waitText(long seconds) {
        if (seconds < 60) return seconds + " giây";
        long minutes = (seconds + 59) / 60;
        return minutes + " phút";
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
