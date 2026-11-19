package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.service.account.AuthStateStore;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.IdentityProviderErrorReason;
import com.carenest.utils.enums.UserStatus;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Mở lại hồ sơ trẻ ⇒ chỉ mở lại tài khoản phụ huynh do hệ thống khóa khi trẻ nghỉ (CHD-04, V90). */
class ParentAccountReadmitListenerTest {

    private static final Long CHILD = 301L;

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final GuardianChildLinkService links = mock(GuardianChildLinkService.class);
    private final IdentityProviderClient identity = mock(IdentityProviderClient.class);
    private final AuthStateStore authState = mock(AuthStateStore.class);
    private final ParentAccountReadmitListener listener = new ParentAccountReadmitListener(
            accounts,
            links,
            new AccountLifecycleOperations(
                    accounts, identity, authState, new TransactionTemplate(mock(PlatformTransactionManager.class))));

    @Test
    @DisplayName("Hệ thống khóa khi trẻ nghỉ ⇒ mở lại hồ sơ thì tài khoản ACTIVE, bật lại Keycloak, bỏ cờ khóa")
    void systemLockedParentIsReactivated() {
        UserAccount parent = account(71L, UserStatus.INACTIVE, true);
        guardians(parent);

        listener.onChildReadmitted(new ChildIntakeService.ChildReadmittedEvent(CHILD));

        assertThat(parent.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(parent.isLockedBySystem()).isFalse();
        verify(accounts).saveAndFlush(parent);
        verify(identity).setUserEnabled("kc-71", true);
    }

    @Test
    @DisplayName("HT/PHT khóa tay ⇒ mở lại hồ sơ không mở tài khoản")
    void manuallyLockedParentStaysLocked() {
        UserAccount parent = account(72L, UserStatus.INACTIVE, false);
        guardians(parent);

        listener.onChildReadmitted(new ChildIntakeService.ChildReadmittedEvent(CHILD));

        assertThat(parent.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(accounts, never()).saveAndFlush(any());
        verify(identity, never()).setUserEnabled(any(), any(Boolean.class));
    }

    @Test
    @DisplayName("Tài khoản đang hoạt động ⇒ không ghi gì")
    void activeParentIsUntouched() {
        UserAccount parent = account(73L, UserStatus.ACTIVE, false);
        guardians(parent);

        listener.onChildReadmitted(new ChildIntakeService.ChildReadmittedEvent(CHILD));

        verify(accounts, never()).saveAndFlush(any());
        verify(identity, never()).setUserEnabled(any(), any(Boolean.class));
    }

    @Test
    @DisplayName("Keycloak lỗi ⇒ DB vẫn ACTIVE (đăng nhập lại được khi Keycloak đồng bộ), không ném lỗi")
    void keycloakFailureStillReactivatesInDatabase() {
        UserAccount parent = account(74L, UserStatus.INACTIVE, true);
        guardians(parent);
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null))
                .given(identity)
                .setUserEnabled("kc-74", true);

        listener.onChildReadmitted(new ChildIntakeService.ChildReadmittedEvent(CHILD));

        assertThat(parent.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(accounts).saveAndFlush(parent);
    }

    private void guardians(UserAccount... guardians) {
        given(links.findLinksOfChild(CHILD))
                .willReturn(Arrays.stream(guardians)
                        .map(g -> new GuardianLinkResponse(
                                g.getId(), CHILD, GuardianRelationship.MOTHER, Instant.parse("2026-09-01T00:00:00Z")))
                        .toList());
        given(accounts.findAllById(any())).willReturn(Arrays.asList(guardians));
    }

    private static UserAccount account(Long id, UserStatus status, boolean lockedBySystem) {
        UserAccount account = UserAccount.create("kc-" + id, "09130000" + (id % 100), null, "PH", CareNestRole.PARENT);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "status", status);
        ReflectionTestUtils.setField(account, "lockedBySystem", lockedBySystem);
        return account;
    }
}
