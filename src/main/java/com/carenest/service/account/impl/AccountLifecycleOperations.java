package com.carenest.service.account.impl;

import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.service.account.AuthStateStore;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Thao tác vòng đời tài khoản dùng chung cho nhân sự và phụ huynh: ghi cả Keycloak lẫn PostgreSQL nhưng không có
 * transaction chung ⇒ bù trừ (ADR-0011). Không giữ transaction DB trong lúc gọi HTTP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AccountLifecycleOperations {

    private final UserAccountRepository userAccountRepository;
    private final IdentityProviderClient identityProviderClient;
    private final AuthStateStore authState;
    private final TransactionTemplate transactionTemplate;

    /**
     * Tạo user Keycloak + realm role, rồi chạy {@code persist(keycloakUserId)} trong một transaction DB. Mọi lỗi sau
     * khi đã tạo user Keycloak (kể cả DB mất kết nối) đều xóa user đó, không để user mồ côi.
     */
    <T> T createIdentity(String logPrefix, NewIdentityUser user, CareNestRole role, Function<String, T> persist) {
        String keycloakUserId;
        try {
            keycloakUserId = identityProviderClient.createUser(user);
        } catch (IdentityProviderException ex) {
            throw toGlobalException(ex);
        }
        try {
            identityProviderClient.assignRealmRole(keycloakUserId, role.name());
            return transactionTemplate.execute(status -> persist.apply(keycloakUserId));
        } catch (RuntimeException ex) {
            deleteIdentityQuietly(logPrefix, keycloakUserId);
            if (ex instanceof IdentityProviderException idpEx) {
                throw toGlobalException(idpEx);
            }
            if (ex instanceof DataIntegrityViolationException) {
                throw new GlobalException(ApiCode.CONFLICT, "Account already exists", ex);
            }
            throw ex;
        }
    }

    /** Ghi DB trước: CurrentUserService chặn ngay từ request kế tiếp kể cả khi bước Keycloak lỗi. */
    void applyStatus(String logPrefix, UserAccount account, UserStatus status) {
        account.changeStatus(status);
        userAccountRepository.saveAndFlush(account);
        if (status == UserStatus.INACTIVE) authState.revokeAccountAuth(account.getId());
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
    }

    /** Đặt mật khẩu tạm rồi hủy mọi phiên web gateway và session Keycloak (mật khẩu cũ có thể đã lộ). */
    void resetTemporaryPassword(String logPrefix, UserAccount account, String temporaryPassword) {
        try {
            identityProviderClient.resetTemporaryPassword(account.getKeycloakUserId(), temporaryPassword);
        } catch (IdentityProviderException ex) {
            throw toGlobalException(ex);
        }
        authState.revokeAccountAuth(account.getId());
        try {
            identityProviderClient.logoutUser(account.getKeycloakUserId());
        } catch (IdentityProviderException ex) {
            log.error("{}|identityProviderLogoutFailed", logPrefix, ex);
            throw new GlobalException(
                    ApiCode.SERVICE_UNAVAILABLE,
                    "Password reset but existing sessions could not be revoked, please retry",
                    ex);
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

    static GlobalException toGlobalException(IdentityProviderException ex) {
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
