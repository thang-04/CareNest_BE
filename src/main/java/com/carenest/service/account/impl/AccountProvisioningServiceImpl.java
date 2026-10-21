package com.carenest.service.account.impl;

import com.carenest.config.AuthProperties;
import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.CreateAccountRequest;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.AuthGatewayClient;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.mapper.account.AccountMapper;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountProvisioningService;
import com.carenest.service.account.AuthStateStore;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Cấp/khóa tài khoản: ghi cả Keycloak lẫn PostgreSQL nhưng không có transaction chung, nên dùng bù trừ
 * (tạo Keycloak lỗi bước sau ⇒ xóa user Keycloak) thay vì distributed transaction (ADR-0011).
 * Không đặt @Transactional ở mức method để không giữ kết nối DB trong lúc gọi HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountProvisioningServiceImpl implements AccountProvisioningService {
    private static final String LOG_PREFIX = "[AccountProvisioningServiceImpl]";

    // Tài khoản ADMIN chỉ tạo trực tiếp trên Keycloak (bootstrap), không mở qua API
    private static final Set<CareNestRole> PROVISIONABLE_ROLES = EnumSet.of(
            CareNestRole.PRINCIPAL,
            CareNestRole.VICE_PRINCIPAL,
            CareNestRole.TEACHER,
            CareNestRole.KITCHEN_STAFF,
            CareNestRole.PARENT);

    private final UserAccountRepository userAccountRepository;
    private final IdentityProviderClient identityProviderClient;
    private final AccountMapper accountMapper;
    private final AuthProperties authProperties;
    private final AuthGatewayClient authGatewayClient;
    private final AuthStateStore authState;

    @Override
    public CreateAccountResponse createAccount(CurrentUser actor, CreateAccountRequest request) {
        String logPrefix = LOG_PREFIX + "|createAccount|actorUserId=" + actor.userId() + "|role=" + request.role();
        log.info("{}|START", logPrefix);
        try {
            if (!PROVISIONABLE_ROLES.contains(request.role())) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "This role cannot be created via API");
            }
            String username = request.username().trim().toLowerCase(Locale.ROOT);
            String email = StringUtils.hasText(request.email())
                    ? request.email().trim().toLowerCase(Locale.ROOT)
                    : null;
            boolean sendSetupEmail = email != null && identityProviderClient.canSendSetupEmail();
            if (request.temporaryPassword() == null && !sendSetupEmail) {
                throw new GlobalException(
                        ApiCode.BAD_REQUEST, "Temporary password is required when a setup email cannot be sent");
            }
            if (userAccountRepository.existsByUsername(username)) {
                throw new GlobalException(ApiCode.CONFLICT, "Username already exists");
            }

            String keycloakUserId;
            try {
                keycloakUserId = identityProviderClient.createUser(
                        new NewIdentityUser(username, email, request.fullName().trim(), request.temporaryPassword()));
            } catch (IdentityProviderException ex) {
                throw toGlobalException(ex);
            }

            UserAccount account;
            try {
                identityProviderClient.assignRealmRole(
                        keycloakUserId, request.role().name());
                account = userAccountRepository.saveAndFlush(UserAccount.create(
                        keycloakUserId, username, email, request.fullName().trim()));
            } catch (RuntimeException ex) {
                // Mọi lỗi sau khi đã tạo user Keycloak (kể cả DB mất kết nối) đều phải bù trừ, không để user mồ côi
                deleteIdentityQuietly(logPrefix, keycloakUserId);
                if (ex instanceof IdentityProviderException idpEx) {
                    throw toGlobalException(idpEx);
                }
                if (ex instanceof DataIntegrityViolationException) {
                    throw new GlobalException(ApiCode.CONFLICT, "Account already exists", ex);
                }
                throw ex;
            }

            boolean setupEmailSent = sendSetupEmail && sendSetupEmailQuietly(logPrefix, keycloakUserId);
            log.info(
                    "{}|userId={}|keycloakUserId={}|setupEmailSent={}|END",
                    logPrefix,
                    account.getId(),
                    keycloakUserId,
                    setupEmailSent);
            return accountMapper.toCreateAccountResponse(account, request.role(), setupEmailSent);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AccountResponse updateAccountStatus(CurrentUser actor, Long userId, UserStatus status) {
        String logPrefix = LOG_PREFIX + "|updateAccountStatus|actorUserId=" + actor.userId() + "|userId=" + userId
                + "|status=" + status;
        log.info("{}|START", logPrefix);
        try {
            // ADMIN tự khóa mình ⇒ có thể không còn ai mở lại qua API (team chốt 2026-10-06)
            if (userId.equals(actor.userId())) {
                throw new GlobalException(ApiCode.BAD_REQUEST, "You cannot change the status of your own account");
            }
            UserAccount account = userAccountRepository
                    .findById(userId)
                    .orElseThrow(() -> new GlobalException(ApiCode.NOT_FOUND, "Account not found"));
            // Ghi DB trước: CurrentUserService chặn ngay từ request kế tiếp kể cả khi bước Keycloak lỗi
            account.changeStatus(status);
            userAccountRepository.saveAndFlush(account);
            if (status == UserStatus.INACTIVE) authState.revokeAccountAuth(userId);
            try {
                identityProviderClient.setUserEnabled(account.getKeycloakUserId(), status == UserStatus.ACTIVE);
                if (status == UserStatus.INACTIVE) {
                    identityProviderClient.logoutUser(account.getKeycloakUserId());
                }
            } catch (IdentityProviderException ex) {
                log.error("{}|identityProviderSyncFailed", logPrefix, ex);
                throw new GlobalException(
                        ApiCode.SERVICE_UNAVAILABLE,
                        "Account status saved but identity provider sync failed, please retry",
                        ex);
            }

            log.info("{}|END", logPrefix);
            return accountMapper.toAccountResponse(account);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private void deleteIdentityQuietly(String logPrefix, String keycloakUserId) {
        try {
            identityProviderClient.deleteUser(keycloakUserId);
            log.warn("{}|keycloakUserId={}|compensated", logPrefix, keycloakUserId);
        } catch (IdentityProviderException ex) {
            // User Keycloak mồ côi chỉ dẫn tới 403 "not provisioned" khi đăng nhập, nhưng cần dọn tay
            log.error("{}|keycloakUserId={}|compensationFailed|manualCleanupRequired", logPrefix, keycloakUserId, ex);
        }
    }

    // Email lỗi không hủy tài khoản. Nếu không có mật khẩu tạm, người dùng dùng "Quên mật khẩu" ở trang đăng nhập
    // hoặc Admin đặt lại trên Keycloak Console (chưa có API gửi lại)
    private boolean sendSetupEmailQuietly(String logPrefix, String keycloakUserId) {
        try {
            if (authProperties.enabled()) authGatewayClient.sendReactSetupEmail(keycloakUserId);
            else identityProviderClient.sendSetupEmail(keycloakUserId);
            return true;
        } catch (IdentityProviderException | AuthGatewayClient.ProviderUnavailable ex) {
            log.warn("{}|keycloakUserId={}|setupEmailFailed", logPrefix, keycloakUserId);
            return false;
        }
    }

    private static GlobalException toGlobalException(IdentityProviderException ex) {
        return switch (ex.getReason()) {
            case CONFLICT -> new GlobalException(ApiCode.CONFLICT, "Username or email already exists", ex);
            case REJECTED ->
                new GlobalException(
                        ApiCode.BAD_REQUEST,
                        "Account data rejected by identity provider (check password policy and full name)",
                        ex);
            case UNAVAILABLE -> new GlobalException(ApiCode.SERVICE_UNAVAILABLE, "Identity provider unavailable", ex);
        };
    }
}
