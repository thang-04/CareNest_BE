package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.child.AddGuardianRequest;
import com.carenest.dto.child.AddGuardianResponse;
import com.carenest.dto.child.GuardianCandidateResponse;
import com.carenest.dto.child.ParentPasswordResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.mapper.account.AccountMapper;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.AuthStateStore;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class GuardianAccountServiceImplTest {

    private static final CurrentUser VICE =
            new CurrentUser(3L, "kc-3", "vp", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL));
    private static final Long CHILD = 101L;
    private static final AddGuardianRequest REQUEST =
            new AddGuardianRequest("+84 912 345 678", "Phụ huynh Demo", GuardianRelationship.MOTHER, null);

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final GuardianChildLinkService links = mock(GuardianChildLinkService.class);
    private final ChildService children = mock(ChildService.class);
    private final StaffAccessPolicy policy = mock(StaffAccessPolicy.class);
    private final IdentityProviderClient identity = mock(IdentityProviderClient.class);
    private final AuthStateStore authState = mock(AuthStateStore.class);
    private final TemporaryPasswordGenerator passwords = mock(TemporaryPasswordGenerator.class);
    private final GuardianAccountServiceImpl service = new GuardianAccountServiceImpl(
            accounts,
            links,
            children,
            policy,
            new AccountLifecycleOperations(
                    accounts, identity, authState, new TransactionTemplate(mock(PlatformTransactionManager.class))),
            passwords,
            new AccountMapper());

    @BeforeEach
    void setUp() {
        given(children.childExists(CHILD)).willReturn(true);
        given(passwords.generate()).willReturn("Gen3rated7k");
        given(accounts.saveAndFlush(any())).willAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 50L);
            return account;
        });
    }

    @Test
    @DisplayName("AUTH-18: SĐT mới ⇒ tạo tài khoản PARENT username = SĐT chuẩn hóa, gắn trẻ, trả mật khẩu tạm 1 lần")
    void createsParentAccountWithGeneratedPassword() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.empty());
        given(identity.createUser(any())).willReturn("kc-parent");

        AddGuardianResponse response = service.addGuardian(VICE, CHILD, REQUEST);

        verify(policy).checkCanManageChildGuardians(VICE, CHILD);
        ArgumentCaptor<NewIdentityUser> user = ArgumentCaptor.forClass(NewIdentityUser.class);
        InOrder order = inOrder(identity, accounts, links);
        order.verify(identity).createUser(user.capture());
        order.verify(identity).assignRealmRole("kc-parent", "PARENT");
        order.verify(accounts).saveAndFlush(any());
        order.verify(links).createLink(50L, CHILD, GuardianRelationship.MOTHER);
        assertThat(user.getValue().username()).isEqualTo("0912345678");
        assertThat(user.getValue().email()).isNull();
        assertThat(user.getValue().temporaryPassword()).isEqualTo("Gen3rated7k");
        assertThat(response.newAccount()).isTrue();
        assertThat(response.temporaryPassword()).isEqualTo("Gen3rated7k");
        assertThat(response.toString()).doesNotContain("Gen3rated7k");
    }

    @Test
    @DisplayName("AUTH-18: SĐT đã là phụ huynh, đã xác nhận ⇒ chỉ thêm liên kết, không tạo user, không trả mật khẩu")
    void existingParentIsOnlyLinked() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(account(60L, CareNestRole.PARENT)));

        AddGuardianResponse response = service.addGuardian(VICE, CHILD, confirmed(60L));

        verify(links).createLink(60L, CHILD, GuardianRelationship.MOTHER);
        verifyNoInteractions(identity);
        assertThat(response.newAccount()).isFalse();
        assertThat(response.temporaryPassword()).isNull();
    }

    @Test
    @DisplayName("AUTH-18: SĐT đã có tài khoản mà chưa xác nhận (hoặc xác nhận sai người) ⇒ 409, không liên kết")
    void existingAccountNeedsConfirmation() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(account(60L, CareNestRole.PARENT)));

        assertThatThrownBy(() -> service.addGuardian(VICE, CHILD, REQUEST))
                .extracting("apiCode")
                .isEqualTo(ApiCode.GUARDIAN_ACCOUNT_CONFIRMATION_REQUIRED);
        assertThatThrownBy(() -> service.addGuardian(VICE, CHILD, confirmed(99L)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.GUARDIAN_ACCOUNT_CONFIRMATION_REQUIRED);
        verify(links, never()).createLink(any(), any(), any());
        verifyNoInteractions(identity);
    }

    @Test
    @DisplayName("AUTH-18: SĐT của GV ⇒ một tài khoản: thêm role PARENT, giữ role GV, không tạo user mới")
    void staffAccountGetsParentRole() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(account(61L, CareNestRole.TEACHER)));
        given(identity.getRealmRoleNames("kc-61")).willReturn(List.of("TEACHER", "offline_access"));

        AddGuardianResponse response = service.addGuardian(VICE, CHILD, confirmed(61L));

        verify(policy).checkCanManageAccount(VICE, 61L, CareNestRole.TEACHER);
        InOrder order = inOrder(identity, links);
        order.verify(identity).assignRealmRole("kc-61", "PARENT");
        order.verify(links).createLink(61L, CHILD, GuardianRelationship.MOTHER);
        verify(identity, never()).createUser(any());
        verify(identity, never()).removeRealmRole(any(), any());
        assertThat(response.newAccount()).isFalse();
        assertThat(response.temporaryPassword()).isNull();
    }

    @Test
    @DisplayName("Liên kết tài khoản nhân sự lỗi (đã liên kết) ⇒ gỡ role PARENT vừa thêm")
    void staffLinkFailureRemovesGrantedParentRole() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(account(61L, CareNestRole.TEACHER)));
        given(identity.getRealmRoleNames("kc-61")).willReturn(List.of("TEACHER"));
        willThrow(GlobalException.of(ApiCode.GUARDIAN_ALREADY_LINKED))
                .given(links)
                .createLink(61L, CHILD, GuardianRelationship.MOTHER);

        assertThatThrownBy(() -> service.addGuardian(VICE, CHILD, confirmed(61L)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.GUARDIAN_ALREADY_LINKED);
        verify(identity).removeRealmRole("kc-61", "PARENT");
    }

    @Test
    void staffAccountAlreadyParentIsNotReassigned() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(account(61L, CareNestRole.TEACHER)));
        given(identity.getRealmRoleNames("kc-61")).willReturn(List.of("TEACHER", "PARENT"));

        service.addGuardian(VICE, CHILD, confirmed(61L));

        verify(identity, never()).assignRealmRole(any(), any());
        verify(links).createLink(61L, CHILD, GuardianRelationship.MOTHER);
    }

    @Test
    @DisplayName("AUTH-17: tài khoản nhân sự ngoài phạm vi quản lý ⇒ không thêm role, không liên kết")
    void staffAccountOutOfScopeIsDenied() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(account(61L, CareNestRole.TEACHER)));
        willThrow(GlobalException.of(ApiCode.ACCOUNT_MANAGE_DENIED))
                .given(policy)
                .checkCanManageAccount(VICE, 61L, CareNestRole.TEACHER);

        assertThatThrownBy(() -> service.addGuardian(VICE, CHILD, confirmed(61L)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ACCOUNT_MANAGE_DENIED);
        verifyNoInteractions(identity);
        verify(links, never()).createLink(any(), any(), any());
    }

    @Test
    @DisplayName("AUTH-12: không tự liên kết tài khoản của chính mình làm phụ huynh")
    void cannotLinkOwnAccount() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(account(3L, CareNestRole.VICE_PRINCIPAL)));

        assertThatThrownBy(() -> service.addGuardian(VICE, CHILD, confirmed(3L)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CANNOT_MANAGE_OWN_ACCOUNT);
        verifyNoInteractions(identity);
    }

    @Test
    @DisplayName("AUTH-18: tra SĐT trả họ tên đã che để người nhập xác nhận đúng người")
    void candidateLookupMasksFullName() {
        UserAccount teacher = UserAccount.create("kc-61", "0912345678", null, "Nguyễn Thị Lan", CareNestRole.TEACHER);
        ReflectionTestUtils.setField(teacher, "id", 61L);
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(teacher));
        given(links.isGuardianOf(61L, CHILD)).willReturn(false);

        GuardianCandidateResponse candidate = service.findGuardianCandidate(VICE, CHILD, "0912.345.678");

        assertThat(candidate.exists()).isTrue();
        assertThat(candidate.userId()).isEqualTo(61L);
        assertThat(candidate.maskedFullName()).isEqualTo("N***** T** L**");
        assertThat(candidate.staffAccount()).isTrue();
        assertThat(candidate.alreadyLinked()).isFalse();
    }

    @Test
    void candidateLookupForUnknownPhoneReturnsNone() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.empty());

        assertThat(service.findGuardianCandidate(VICE, CHILD, "0912345678").exists())
                .isFalse();
    }

    @Test
    void candidateLookupChecksChildScopeFirst() {
        willThrow(GlobalException.of(ApiCode.CHILD_GUARDIAN_MANAGE_DENIED))
                .given(policy)
                .checkCanManageChildGuardians(VICE, CHILD);

        assertThatThrownBy(() -> service.findGuardianCandidate(VICE, CHILD, "0912345678"))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CHILD_GUARDIAN_MANAGE_DENIED);
        verifyNoInteractions(accounts);
    }

    @Test
    void invalidPhoneIsRejectedBeforeKeycloak() {
        assertThatThrownBy(() -> service.addGuardian(
                        VICE, CHILD, new AddGuardianRequest("12345", "PH", GuardianRelationship.FATHER, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PHONE_NUMBER_INVALID);
        verifyNoInteractions(identity);
    }

    @Test
    @DisplayName("AUTH-08: trẻ ngoài campus ⇒ 403 trước khi đụng Keycloak/DB")
    void childOutOfScopeIsDenied() {
        willThrow(GlobalException.of(ApiCode.CHILD_GUARDIAN_MANAGE_DENIED))
                .given(policy)
                .checkCanManageChildGuardians(VICE, CHILD);

        assertThatThrownBy(() -> service.addGuardian(VICE, CHILD, REQUEST))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CHILD_GUARDIAN_MANAGE_DENIED);
        verifyNoInteractions(identity, accounts, links);
    }

    @Test
    void linkFailureAfterKeycloakCreateDeletesKeycloakUser() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.empty());
        given(identity.createUser(any())).willReturn("kc-parent");
        given(links.createLink(any(), any(), any())).willThrow(GlobalException.of(ApiCode.GUARDIAN_ALREADY_LINKED));

        assertThatThrownBy(() -> service.addGuardian(VICE, CHILD, REQUEST))
                .extracting("apiCode")
                .isEqualTo(ApiCode.GUARDIAN_ALREADY_LINKED);
        verify(identity).deleteUser("kc-parent");
    }

    @Test
    @DisplayName("AUTH-19: cấp lại mật khẩu phụ huynh ⇒ mật khẩu mới hệ thống sinh, hủy phiên, trả 1 lần")
    void resetParentPasswordGeneratesNewPassword() {
        given(accounts.findById(60L)).willReturn(Optional.of(account(60L, CareNestRole.PARENT)));

        ParentPasswordResponse response = service.resetParentPassword(VICE, 60L);

        verify(policy).checkCanManageParent(VICE, 60L);
        verify(identity).resetTemporaryPassword("kc-60", "Gen3rated7k");
        verify(authState).revokeAccountAuth(60L);
        verify(identity).logoutUser("kc-60");
        assertThat(response.temporaryPassword()).isEqualTo("Gen3rated7k");
    }

    @Test
    void parentEndpointsRefuseStaffAccountsAndSelf() {
        given(accounts.findById(61L)).willReturn(Optional.of(account(61L, CareNestRole.TEACHER)));

        assertThatThrownBy(() -> service.updateParentStatus(VICE, 61L, UserStatus.INACTIVE))
                .extracting("apiCode")
                .isEqualTo(ApiCode.NOT_PARENT_ACCOUNT);
        assertThatThrownBy(() -> service.resetParentPassword(VICE, 3L))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CANNOT_MANAGE_OWN_ACCOUNT);
        verify(identity, never()).resetTemporaryPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("AUTH-19: gỡ liên kết chỉ xóa liên kết, tài khoản giữ nguyên")
    void removeGuardianOnlyDeletesLink() {
        service.removeGuardian(VICE, CHILD, 60L);

        verify(links).deleteLink(60L, CHILD);
        verifyNoInteractions(identity);
        verify(accounts, never()).delete(any(UserAccount.class));
    }

    private static AddGuardianRequest confirmed(Long userId) {
        return new AddGuardianRequest(REQUEST.phone(), REQUEST.fullName(), REQUEST.relationship(), userId);
    }

    private static UserAccount account(Long id, CareNestRole role) {
        UserAccount account = UserAccount.create("kc-" + id, "u" + id, null, "Người dùng", role);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
