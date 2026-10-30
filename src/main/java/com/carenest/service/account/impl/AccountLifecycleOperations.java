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
import java.util.function.Supplier;
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
                throw GlobalException.withCause(ApiCode.ACCOUNT_EXISTS, ex);
            }
            throw ex;
        }
    }

    /**
     * Thêm realm role (nếu chưa có) rồi ghi DB trong một transaction. DB lỗi ⇒ gỡ role vừa thêm, không để tài khoản
     * mang role nhân sự/phụ huynh mà DB không biết (API thu hồi dựa trên DB nên không gỡ được role mồ côi).
     */
    <T> T addRealmRole(String logPrefix, UserAccount account, CareNestRole role, Supplier<T> persist) {
        boolean granted;
        try {
            granted = !identityProviderClient
                    .getRealmRoleNames(account.getKeycloakUserId())
                    .contains(role.name());
            if (granted) identityProviderClient.assignRealmRole(account.getKeycloakUserId(), role.name());
        } catch (IdentityProviderException ex) {
            throw toGlobalException(ex);
        }
        try {
            return transactionTemplate.execute(status -> persist.get());
        } catch (RuntimeException ex) {
            if (granted) removeRealmRoleQuietly(logPrefix, account, role);
            throw ex;
        }
    }

    /**
     * Gỡ realm role trước rồi mới ghi DB: DB lỗi thì gọi lại vẫn chạy (gỡ role là idempotent), còn token mới không
     * mang role đã gỡ. Sau cùng hủy mọi phiên để token cũ còn role không dùng tiếp được.
     */
    void revokeRealmRole(String logPrefix, UserAccount account, CareNestRole role, Runnable persist) {
        try {
            identityProviderClient.removeRealmRole(account.getKeycloakUserId(), role.name());
        } catch (IdentityProviderException ex) {
            throw toGlobalException(ex);
        }
        transactionTemplate.executeWithoutResult(status -> persist.run());
        authState.revokeAccountAuth(account.getId());
        try {
            identityProviderClient.logoutUser(account.getKeycloakUserId());
        } catch (IdentityProviderException ex) {
            // DB đã ghi, role đã gỡ: refresh token Keycloak cấp lại token không còn role đó nên chỉ log, không báo lỗi
            log.error("{}|identityProviderLogoutFailed", logPrefix, ex);
        }
    }

    /**
     * Khóa tài khoản do hệ thống tự làm (không có người chờ kết quả): DB INACTIVE + hủy phiên web trong một
     * transaction riêng, rồi mới gọi Keycloak ngoài transaction; Keycloak lỗi chỉ log vì CurrentUserService đã chặn
     * tài khoản INACTIVE ở mọi request.
     */
    void deactivateQuietly(String logPrefix, UserAccount account) {
        transactionTemplate.executeWithoutResult(status -> {
            account.changeStatus(UserStatus.INACTIVE);
            userAccountRepository.saveAndFlush(account);
            authState.revokeAccountAuth(account.getId());
        });
        try {
            identityProviderClient.setUserEnabled(account.getKeycloakUserId(), false);
            identityProviderClient.logoutUser(account.getKeycloakUserId());
        } catch (IdentityProviderException ex) {
            log.error("{}|identityProviderSyncFailed", logPrefix, ex);
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
            throw GlobalException.withCause(ApiCode.IDENTITY_SYNC_FAILED, ex);
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
            throw GlobalException.withCause(ApiCode.PASSWORD_RESET_SESSION_REVOKE_FAILED, ex);
        }
    }

    private void removeRealmRoleQuietly(String logPrefix, UserAccount account, CareNestRole role) {
        try {
            identityProviderClient.removeRealmRole(account.getKeycloakUserId(), role.name());
            log.warn("{}|userId={}|role={}|compensated", logPrefix, account.getId(), role);
        } catch (IdentityProviderException ex) {
            log.error(
                    "{}|userId={}|role={}|compensationFailed|manualCleanupRequired",
                    logPrefix,
                    account.getId(),
                    role,
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
            case CONFLICT -> GlobalException.withCause(ApiCode.USERNAME_OR_EMAIL_EXISTS, ex);
            case REJECTED -> GlobalException.withCause(ApiCode.IDENTITY_DATA_REJECTED, ex);
            case UNAVAILABLE -> GlobalException.withCause(ApiCode.IDENTITY_PROVIDER_UNAVAILABLE, ex);
        };
    }
}
