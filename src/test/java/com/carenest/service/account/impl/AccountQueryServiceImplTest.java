package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.entity.account.UserAccount;
import com.carenest.mapper.account.AccountMapper;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    private static final CurrentUser PRINCIPAL =
            new CurrentUser(2L, "kc-2", "ht", "HT", null, Set.of(CareNestRole.PRINCIPAL));
    private static final CurrentUser ADMIN =
            new CurrentUser(1L, "kc-1", "admin", "Admin", null, Set.of(CareNestRole.ADMIN));

    @Test
    @DisplayName("AUTH-17: HT chỉ liệt kê PHT/GV/bếp (không phụ huynh, Admin, HT); Admin liệt kê mọi tài khoản")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void principalListIsLimitedToStaffRoles() {
        given(policy.managesWholeSchool(PRINCIPAL)).willReturn(true);

        ArgumentCaptor<Collection<Object>> roles = ArgumentCaptor.forClass(Collection.class);
        verify(primaryRolePathFor(PRINCIPAL)).in(roles.capture());
        assertThat(roles.getValue())
                .containsExactlyInAnyOrder(
                        CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER, CareNestRole.KITCHEN_STAFF);
        verify(primaryRolePathFor(ADMIN), never()).in(any(Collection.class));
    }

    // Chạy Specification đã gửi xuống repository; lọc role thể hiện qua primaryRole.in(...)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Path<Object> primaryRolePathFor(CurrentUser actor) {
        clearInvocations(accounts);
        given(accounts.findAll(any(Specification.class), any(Pageable.class))).willReturn(Page.empty());
        service.searchAccounts(actor, null, null, null, null, 0, 20);

        ArgumentCaptor<Specification<UserAccount>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(accounts).findAll(spec.capture(), any(Pageable.class));
        Root<UserAccount> root = mock(Root.class);
        Path<Object> primaryRole = mock(Path.class);
        given(root.get("primaryRole")).willReturn(primaryRole);
        spec.getValue().toPredicate(root, mock(CriteriaQuery.class), mock(CriteriaBuilder.class));
        return primaryRole;
    }
}
