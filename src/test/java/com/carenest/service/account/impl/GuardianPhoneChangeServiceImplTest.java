package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.account.PhoneChangeDecisionRequest;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.entity.account.GuardianPhoneChangeRequest;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.repository.account.GuardianPhoneChangeRequestRepository;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.AuthStateStore;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.ChildRecordService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.PhoneChangeStatus;
import com.carenest.utils.enums.SmsStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class GuardianPhoneChangeServiceImplTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final Long REQUEST_ID = 70L;
    private static final Long CHILD_ID = 1L;
    private static final Long CAMPUS_ID = 2L;
    private static final Long REQUESTER = 4L;
    private static final CurrentUser VICE = TestPermissions.userWithDefaults(3L, CareNestRole.VICE_PRINCIPAL);

    private final GuardianPhoneChangeRequestRepository requests = mock(GuardianPhoneChangeRequestRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final ChildIntakeService intake = mock(ChildIntakeService.class);
    private final ChildRecordService records = mock(ChildRecordService.class);
    private final GuardianChildLinkService links = mock(GuardianChildLinkService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final IdentityProviderClient identity = mock(IdentityProviderClient.class);
    private final TemporaryPasswordGenerator passwords = mock(TemporaryPasswordGenerator.class);
    private final ParentSmsSender sms = mock(ParentSmsSender.class);
    private final PhoneChangeSupport support = mock(PhoneChangeSupport.class);
    private final TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
    private final GuardianPhoneChangeServiceImpl service = new GuardianPhoneChangeServiceImpl(
            requests,
            accounts,
            intake,
            records,
            links,
            notifications,
            new AccountLifecycleOperations(accounts, identity, mock(AuthStateStore.class), tx),
            passwords,
            sms,
            support,
            tx,
            CLOCK);

    private GuardianPhoneChangeRequest request;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        request = GuardianPhoneChangeRequest.open(
                50L, CHILD_ID, 31L, "0912345678", "0987654321", "Phụ huynh đổi số", REQUESTER, CLOCK.instant());
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        account = UserAccount.createParent("kc-50", "0912345678", null, "Mẹ An", "001234567890");
        ReflectionTestUtils.setField(account, "id", 50L);
        given(requests.findById(REQUEST_ID)).willReturn(Optional.of(request));
        given(requests.lockById(REQUEST_ID)).willReturn(Optional.of(request));
        given(requests.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(support.childrenOf(anyList()))
                .willReturn(Map.of(
                        CHILD_ID,
                        new GuardianChildSummary(
                                CHILD_ID, "HS26001", "Bé An", ChildStatus.ACTIVE, "Mầm 1", CAMPUS_ID)));
        given(support.inScope(VICE, CAMPUS_ID)).willReturn(true);
        given(support.toResponses(any(), anyList(), any()))
                .willReturn(List.of(mock(com.carenest.dto.account.GuardianPhoneChangeResponse.class)));
        given(accounts.findById(50L)).willReturn(Optional.of(account));
        given(links.isGuardianOf(50L, CHILD_ID)).willReturn(true);
        given(passwords.generate()).willReturn("Tmp9pass");
        given(accounts.updateUsername(50L, "0912345678", "0987654321")).willReturn(1);
        given(sms.sendUsernameChanged(any(), eq("Tmp9pass"))).willReturn(SmsStatus.SENT);
    }

    @Test
    @DisplayName("G94-180/AUTH-21: duyệt ⇒ đổi username + mật khẩu tạm Keycloak, rồi DB, SĐT mọi hồ sơ, SMS sau commit")
    void approveChangesUsernameThenSendsSms() {
        service.approve(VICE, REQUEST_ID);

        InOrder order = inOrder(identity, accounts, records, sms);
        order.verify(identity).updateUsername("kc-50", "0987654321");
        order.verify(identity).resetTemporaryPassword("kc-50", "Tmp9pass");
        order.verify(accounts).updateUsername(50L, "0912345678", "0987654321");
        order.verify(records).changeGuardianPhone(50L, "0987654321");
        order.verify(sms).sendUsernameChanged(any(), eq("Tmp9pass"));
        verify(identity).logoutUser("kc-50");
        assertThat(request.getStatus()).isEqualTo(PhoneChangeStatus.APPROVED);
        assertThat(request.getDecidedBy()).isEqualTo(VICE.userId());
        assertThat(request.getSmsStatus()).isEqualTo(SmsStatus.SENT);
        verify(notifications)
                .notifyUsers(
                        eq(Set.of(REQUESTER)),
                        eq(NotificationType.GUARDIAN_PHONE_CHANGE_DECIDED),
                        any(),
                        any(),
                        eq(CHILD_ID));
    }

    @Test
    @DisplayName("G94-180/AUTH-21 (sửa 2026-11-11): người gửi có quyền duyệt thì tự duyệt được, không tự báo mình")
    void requesterMayApproveOwnRequest() {
        CurrentUser requester = TestPermissions.userWithDefaults(REQUESTER, CareNestRole.VICE_PRINCIPAL);
        given(support.inScope(requester, CAMPUS_ID)).willReturn(true);

        service.approve(requester, REQUEST_ID);

        assertThat(request.getStatus()).isEqualTo(PhoneChangeStatus.APPROVED);
        verify(identity).updateUsername("kc-50", "0987654321");
        verify(notifications, never())
                .notifyUsers(any(), eq(NotificationType.GUARDIAN_PHONE_CHANGE_DECIDED), any(), any(), any());
    }

    @Test
    @DisplayName("G94-180/AUTH-21: số mới đã là tên đăng nhập người khác ⇒ 409, không đổi gì")
    void approveRejectsTakenPhone() {
        given(accounts.existsByUsername("0987654321")).willReturn(true);

        assertThatThrownBy(() -> service.approve(VICE, REQUEST_ID))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.PHONE_CHANGE_REQUEST_STALE));
        verify(identity, never()).updateUsername(any(), any());
    }

    @Test
    @DisplayName("G94-180/AUTH-21: DB lỗi sau khi đổi username Keycloak ⇒ trả username cũ, không gửi SMS")
    void approveRestoresUsernameWhenDatabaseFails() {
        willThrow(new IllegalStateException("db")).given(records).changeGuardianPhone(50L, "0987654321");

        assertThatThrownBy(() -> service.approve(VICE, REQUEST_ID)).isInstanceOf(IllegalStateException.class);

        InOrder order = inOrder(identity);
        order.verify(identity).updateUsername("kc-50", "0987654321");
        order.verify(identity).updateUsername("kc-50", "0912345678");
        verify(sms, never()).sendUsernameChanged(any(), any());
    }

    @Test
    @DisplayName("G94-180/AUTH-21: realm chưa bật sửa username (KC 400 read-only) ⇒ 1140, không đổi DB, không SMS")
    void approveFailsClearlyWhenRealmForbidsUsernameEdit() {
        willThrow(new com.carenest.integration.keycloak.IdentityProviderException(
                        com.carenest.utils.enums.IdentityProviderErrorReason.REJECTED, "read-only", null))
                .given(identity)
                .updateUsername("kc-50", "0987654321");

        assertThatThrownBy(() -> service.approve(VICE, REQUEST_ID))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.USERNAME_CHANGE_NOT_ALLOWED));
        verify(accounts, never()).updateUsername(any(), any(), any());
        verify(identity, never()).resetTemporaryPassword(any(), any());
        verify(sms, never()).sendUsernameChanged(any(), any());
        assertThat(request.getStatus()).isEqualTo(PhoneChangeStatus.PENDING);
    }

    @Test
    @DisplayName("G94-180/AUTH-21: từ chối ⇒ giữ số cũ, báo người gửi kèm ghi chú")
    void rejectKeepsOldPhone() {
        service.reject(VICE, REQUEST_ID, new PhoneChangeDecisionRequest(" Số chưa xác minh "));

        assertThat(request.getStatus()).isEqualTo(PhoneChangeStatus.REJECTED);
        assertThat(request.getDecisionNote()).isEqualTo("Số chưa xác minh");
        verify(identity, never()).updateUsername(any(), any());
        verify(notifications)
                .notifyUsers(
                        eq(Set.of(REQUESTER)),
                        eq(NotificationType.GUARDIAN_PHONE_CHANGE_DECIDED),
                        any(),
                        any(),
                        eq(CHILD_ID));
    }

    @Test
    @DisplayName("PHT ngoài điểm trường của trẻ ⇒ 403")
    void outOfScopeDenied() {
        given(support.inScope(VICE, CAMPUS_ID)).willReturn(false);

        assertThatThrownBy(() -> service.reject(VICE, REQUEST_ID, null))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.CHILD_GUARDIAN_MANAGE_DENIED));
    }
}
