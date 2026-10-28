package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.mapper.account.AccountMapper;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class AccountQueryServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final CurrentUser VICE =
            new CurrentUser(3L, "kc-3", "vp", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL));

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final StaffAssignmentService assignments = mock(StaffAssignmentService.class);
    private final StaffAccessPolicy policy = mock(StaffAccessPolicy.class);
    private final AccountQueryServiceImpl service = new AccountQueryServiceImpl(
            accounts,
            assignments,
            policy,
            new ManagedAccountLoader(accounts, policy),
            new AccountMapper(),
            Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("AUTH-08: PHT chỉ liệt kê nhân sự đang được phân công trong campus mình")
    @SuppressWarnings("unchecked")
    void vicePrincipalListIsLimitedToManagedCampuses() {
        given(policy.managedCampusIds(VICE)).willReturn(Set.of(10L));
        given(assignments.findActiveStaffUserIds(Set.of(10L), TODAY)).willReturn(Set.of(40L));
        given(accounts.findAll(any(Specification.class), any(Pageable.class))).willReturn(Page.empty());

        service.searchAccounts(VICE, null, null, null, null, 0, 20);

        verify(assignments).findActiveStaffUserIds(Set.of(10L), TODAY);
    }

    @Test
    @DisplayName("AUTH-08: PHT lọc theo campus khác ⇒ 403")
    @SuppressWarnings("unchecked")
    void vicePrincipalCannotFilterOtherCampus() {
        given(policy.managedCampusIds(VICE)).willReturn(Set.of(10L));

        assertThatThrownBy(() -> service.searchAccounts(VICE, null, null, 20L, null, 0, 20))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CAMPUS_ACCESS_DENIED);
        verify(accounts, never()).findAll(any(Specification.class), any(Pageable.class));
    }
}
