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
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.child.GuardianResponse;
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
import java.time.Instant;
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
    private static final AddGuardianRequest REQUEST = withEmail(null);

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
    @DisplayName("AUTH-18: SĐT đã là phụ huynh ⇒ chỉ thêm liên kết, không tạo user, không trả mật khẩu")
    void existingParentIsOnlyLinked() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(account(60L, CareNestRole.PARENT)));

        AddGuardianResponse response = service.addGuardian(VICE, CHILD, REQUEST);

        verify(links).createLink(60L, CHILD, GuardianRelationship.MOTHER);
        verifyNoInteractions(identity);
        assertThat(response.newAccount()).isFalse();
        assertThat(response.temporaryPassword()).isNull();
    }

    @Test
    @DisplayName("AUTH-18: email phụ huynh chỉ để liên hệ ⇒ lưu ở CareNest (chữ thường), không gửi lên Keycloak")
    void storesContactEmailOnlyInCareNest() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.empty());
        given(identity.createUser(any())).willReturn("kc-parent");

        AddGuardianResponse response = service.addGuardian(VICE, CHILD, withEmail("Me@Example.TEST"));

        ArgumentCaptor<NewIdentityUser> user = ArgumentCaptor.forClass(NewIdentityUser.class);
        verify(identity).createUser(user.capture());
        assertThat(user.getValue().email()).isNull();
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("me@example.test");
        assertThat(response.email()).isEqualTo("me@example.test");
    }

    @Test
    @DisplayName("AUTH-18: email bỏ trống ⇒ lưu null")
    void blankEmailIsStoredAsNull() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.empty());
        given(identity.createUser(any())).willReturn("kc-parent");

        AddGuardianResponse response = service.addGuardian(VICE, CHILD, withEmail(""));

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isNull();
        assertThat(response.email()).isNull();
    }

    @Test
    @DisplayName("AUTH-18: SĐT đã là phụ huynh ⇒ chỉ thêm liên kết, giữ email của tài khoản")
    void existingParentKeepsAccountEmail() {
        UserAccount parent = account(60L, CareNestRole.PARENT, "old@example.test");
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(parent));

        AddGuardianResponse response = service.addGuardian(VICE, CHILD, withEmail("new@example.test"));

        assertThat(parent.getEmail()).isEqualTo("old@example.test");
        verify(accounts, never()).saveAndFlush(any());
        assertThat(response.email()).isEqualTo("old@example.test");
    }

    @Test
    @DisplayName("AUTH-18: danh sách phụ huynh của trẻ trả email liên hệ")
    void listGuardiansReturnsEmail() {
        given(links.findLinksOfChild(CHILD))
                .willReturn(List.of(new GuardianLinkResponse(60L, CHILD, GuardianRelationship.FATHER, Instant.EPOCH)));
        given(accounts.findAllById(List.of(60L)))
                .willReturn(List.of(account(60L, CareNestRole.PARENT, "me@example.test")));

        List<GuardianResponse> guardians = service.listGuardians(VICE, CHILD);

        assertThat(guardians)
                .singleElement()
                .extracting(GuardianResponse::email)
                .isEqualTo("me@example.test");
    }

    @Test
    @DisplayName("AUTH-18: SĐT trùng tên đăng nhập tài khoản nhân sự cũ ⇒ 409, không gắn trẻ vào tài khoản nhân sự")
    void staffPhoneNumberIsConflict() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.of(account(61L, CareNestRole.TEACHER)));

        assertThatThrownBy(() -> service.addGuardian(VICE, CHILD, REQUEST))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PHONE_OF_STAFF_ACCOUNT);
        verify(links, never()).createLink(any(), any(), any());
    }

    @Test
    void invalidPhoneIsRejectedBeforeKeycloak() {
        assertThatThrownBy(() -> service.addGuardian(
                        VICE, CHILD, new AddGuardianRequest("12345", "PH", GuardianRelationship.FATHER, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PHONE_INVALID);
        verifyNoInteractions(identity);
    }

    @Test
    @DisplayName("AUTH-08: trẻ ngoài campus ⇒ 403 trước khi đụng Keycloak/DB")
    void childOutOfScopeIsDenied() {
        willThrow(GlobalException.of(ApiCode.CHILD_GUARDIANS_MANAGE_DENIED))
                .given(policy)
                .checkCanManageChildGuardians(VICE, CHILD);

        assertThatThrownBy(() -> service.addGuardian(VICE, CHILD, REQUEST))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CHILD_GUARDIANS_MANAGE_DENIED);
        verifyNoInteractions(identity, accounts, links);
    }

    @Test
    void linkFailureAfterKeycloakCreateDeletesKeycloakUser() {
        given(accounts.findByUsername("0912345678")).willReturn(Optional.empty());
        given(identity.createUser(any())).willReturn("kc-parent");
        given(links.createLink(any(), any(), any())).willThrow(GlobalException.of(ApiCode.PARENT_ALREADY_LINKED));

        assertThatThrownBy(() -> service.addGuardian(VICE, CHILD, REQUEST))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PARENT_ALREADY_LINKED);
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

    private static AddGuardianRequest withEmail(String email) {
        return new AddGuardianRequest("+84 912 345 678", "Phụ huynh Demo", GuardianRelationship.MOTHER, email);
    }

    private static UserAccount account(Long id, CareNestRole role) {
        return account(id, role, null);
    }

    private static UserAccount account(Long id, CareNestRole role, String email) {
        UserAccount account = UserAccount.create("kc-" + id, "u" + id, email, "Người dùng", role);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
