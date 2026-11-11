package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.carenest.entity.account.UserAccount;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.service.account.AuthStateStore;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Cờ "hệ thống khóa" (V87): khóa tự động ghi cờ, khóa/mở tay xóa cờ để mở lại hồ sơ không mở nhầm. */
class AccountLifecycleOperationsLockTest {

    private final AccountLifecycleOperations operations = new AccountLifecycleOperations(
            mock(UserAccountRepository.class),
            mock(IdentityProviderClient.class),
            mock(AuthStateStore.class),
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    @Test
    @DisplayName("Hệ thống khóa (không còn con đang học) ⇒ INACTIVE và đánh dấu do hệ thống")
    void quietDeactivationMarksSystemLock() {
        UserAccount parent = account(UserStatus.ACTIVE, false);

        operations.deactivateQuietly("test", parent);

        assertThat(parent.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(parent.isLockedBySystem()).isTrue();
    }

    @Test
    @DisplayName("HT/PHT khóa tay tài khoản hệ thống đã khóa ⇒ bỏ dấu, mở lại hồ sơ không tự mở")
    void manualLockClearsSystemLock() {
        UserAccount parent = account(UserStatus.INACTIVE, true);

        operations.applyStatus("test", parent, UserStatus.INACTIVE);

        assertThat(parent.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(parent.isLockedBySystem()).isFalse();
    }

    @Test
    @DisplayName("HT/PHT mở tay ⇒ ACTIVE, bỏ dấu")
    void manualUnlockClearsSystemLock() {
        UserAccount parent = account(UserStatus.INACTIVE, true);

        operations.applyStatus("test", parent, UserStatus.ACTIVE);

        assertThat(parent.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(parent.isLockedBySystem()).isFalse();
    }

    private static UserAccount account(UserStatus status, boolean lockedBySystem) {
        UserAccount account = UserAccount.create("kc-81", "0913000081", null, "PH", CareNestRole.PARENT);
        ReflectionTestUtils.setField(account, "id", 81L);
        ReflectionTestUtils.setField(account, "status", status);
        ReflectionTestUtils.setField(account, "lockedBySystem", lockedBySystem);
        return account;
    }
}
