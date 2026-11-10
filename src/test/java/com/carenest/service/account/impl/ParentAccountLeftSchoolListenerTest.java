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
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.child.YearTransitionService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.IdentityProviderErrorReason;
import com.carenest.utils.enums.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ParentAccountLeftSchoolListenerTest {

    private static final LocalDate START = LocalDate.of(2027, 9, 5);
    private static final Long LEAVING = 201L;
    private static final Long STILL_STUDYING = 202L;

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final GuardianChildLinkService links = mock(GuardianChildLinkService.class);
    private final EnrollmentService enrollments = mock(EnrollmentService.class);
    private final IdentityProviderClient identity = mock(IdentityProviderClient.class);
    private final AuthStateStore authState = mock(AuthStateStore.class);
    private final ParentAccountLeftSchoolListener listener = new ParentAccountLeftSchoolListener(
            accounts,
            links,
            enrollments,
            new AccountLifecycleOperations(
                    accounts, identity, authState, new TransactionTemplate(mock(PlatformTransactionManager.class))));

    @Test
    @DisplayName("CHD-04: phụ huynh không còn con nào đang học ⇒ tài khoản INACTIVE, hủy phiên")
    void parentWithoutStudyingChildIsDeactivated() {
        UserAccount parent = account(61L, CareNestRole.PARENT);
        guardians(LEAVING, parent);
        given(links.findChildIdsOfGuardian(61L)).willReturn(List.of(LEAVING));
        given(enrollments.findChildIdsLeftSchool(List.of(LEAVING), START)).willReturn(Set.of(LEAVING));

        listener.onChildrenLeftSchool(new YearTransitionService.ChildrenLeftSchoolEvent(List.of(LEAVING), START));

        assertThat(parent.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(authState).revokeAccountAuth(61L);
        verify(identity).setUserEnabled("kc-61", false);
        verify(identity).logoutUser("kc-61");
    }

    @Test
    @DisplayName("CHD-04: còn con khác đang học ⇒ giữ tài khoản (chỉ mất quyền xem trẻ đã ra trường)")
    void parentWithAnotherStudyingChildStaysActive() {
        UserAccount parent = account(62L, CareNestRole.PARENT);
        guardians(LEAVING, parent);
        given(links.findChildIdsOfGuardian(62L)).willReturn(List.of(LEAVING, STILL_STUDYING));
        given(enrollments.findChildIdsLeftSchool(List.of(LEAVING, STILL_STUDYING), START))
                .willReturn(Set.of(LEAVING));

        listener.onChildrenLeftSchool(new YearTransitionService.ChildrenLeftSchoolEvent(List.of(LEAVING), START));

        assertThat(parent.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(identity, never()).setUserEnabled(any(), any(Boolean.class));
    }

    @Test
    @DisplayName("GV kiêm phụ huynh: con ra trường không khóa tài khoản nhân sự")
    void staffParentIsNotDeactivated() {
        UserAccount teacher = account(63L, CareNestRole.TEACHER);
        guardians(LEAVING, teacher);

        listener.onChildrenLeftSchool(new YearTransitionService.ChildrenLeftSchoolEvent(List.of(LEAVING), START));

        assertThat(teacher.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void keycloakFailureStillLeavesAccountInactiveInDatabase() {
        UserAccount parent = account(64L, CareNestRole.PARENT);
        guardians(LEAVING, parent);
        given(links.findChildIdsOfGuardian(64L)).willReturn(List.of(LEAVING));
        given(enrollments.findChildIdsLeftSchool(List.of(LEAVING), START)).willReturn(Set.of(LEAVING));
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null))
                .given(identity)
                .setUserEnabled("kc-64", false);

        listener.onChildrenLeftSchool(new YearTransitionService.ChildrenLeftSchoolEvent(List.of(LEAVING), START));

        assertThat(parent.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(accounts).saveAndFlush(parent);
    }

    private void guardians(Long childId, UserAccount... guardians) {
        given(links.findLinksOfChild(childId))
                .willReturn(java.util.Arrays.stream(guardians)
                        .map(g -> new GuardianLinkResponse(
                                g.getId(), childId, GuardianRelationship.MOTHER, Instant.parse("2026-09-01T00:00:00Z")))
                        .toList());
        given(accounts.findAllById(any())).willReturn(List.of(guardians));
    }

    private static UserAccount account(Long id, CareNestRole role) {
        UserAccount account = UserAccount.create("kc-" + id, "09120000" + (id % 100), null, "PH", role);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
