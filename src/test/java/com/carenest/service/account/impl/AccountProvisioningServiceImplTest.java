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

import com.carenest.config.AuthProperties;
import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.CreateAccountRequest;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.dto.account.StaffAssignmentScopeRequest;
import com.carenest.dto.organization.NewStaffAssignment;
import com.carenest.dto.organization.StaffAssignmentResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.AuthGatewayClient;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.mapper.account.AccountMapper;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.account.AuthStateStore;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.IdentityProviderErrorReason;
import com.carenest.utils.enums.StaffRole;
import com.carenest.utils.enums.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AccountProvisioningServiceImplTest {

    private static final CurrentUser ADMIN =
            new CurrentUser(1L, "kc-admin", "admin.dev", "Admin", null, Set.of(CareNestRole.ADMIN));
    private static final String KC_ID = "kc-new-teacher";

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final IdentityProviderClient identityProviderClient = mock(IdentityProviderClient.class);
    private final AuthProperties authProperties = mock(AuthProperties.class);
    private final AuthGatewayClient authGatewayClient = mock(AuthGatewayClient.class);
    private final AuthStateStore authState = mock(AuthStateStore.class);
    private final StaffAccessPolicy staffAccessPolicy = mock(StaffAccessPolicy.class);
    private final StaffAssignmentService staffAssignmentService = mock(StaffAssignmentService.class);
    private final AccountProvisioningServiceImpl service = new AccountProvisioningServiceImpl(
            userAccountRepository,
            identityProviderClient,
            new AccountMapper(),
            authProperties,
            authGatewayClient,
            staffAccessPolicy,
            staffAssignmentService,
            new ManagedAccountLoader(userAccountRepository, staffAccessPolicy),
            new AccountLifecycleOperations(
                    userAccountRepository,
                    identityProviderClient,
                    authState,
                    new TransactionTemplate(mock(PlatformTransactionManager.class))),
            Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUpRepository() {
        given(userAccountRepository.saveAndFlush(any())).willAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 42L);
            return account;
        });
    }

    @Test
    void createsKeycloakUserAssignsRoleThenSavesAccount() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);

        CreateAccountResponse response = service.createAccount(
                ADMIN, request(" Teacher.C ", "Teacher.C@Example.TEST", CareNestRole.TEACHER, "Temp-Pass-1"));

        ArgumentCaptor<NewIdentityUser> identity = ArgumentCaptor.forClass(NewIdentityUser.class);
        InOrder order = inOrder(identityProviderClient, userAccountRepository);
        order.verify(identityProviderClient).createUser(identity.capture());
        order.verify(identityProviderClient).assignRealmRole(KC_ID, "TEACHER");
        order.verify(userAccountRepository).saveAndFlush(any());
        assertThat(identity.getValue().username()).isEqualTo("teacher.c");
        assertThat(identity.getValue().email()).isEqualTo("teacher.c@example.test");
        assertThat(identity.getValue().temporaryPassword()).isEqualTo("Temp-Pass-1");
        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.role()).isEqualTo(CareNestRole.TEACHER);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.setupEmailSent()).isFalse();
        verify(identityProviderClient, never()).sendSetupEmail(anyString());
    }

    @Test
    void sendsSetupEmailWhenEnabledAndEmailPresentEvenWithoutTemporaryPassword() {
        given(identityProviderClient.canSendSetupEmail()).willReturn(true);
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);

        CreateAccountResponse response = service.createAccount(
                ADMIN, request("kitchen.c", "kitchen.c@example.test", CareNestRole.KITCHEN_STAFF, null));

        verify(identityProviderClient).sendSetupEmail(KC_ID);
        assertThat(response.setupEmailSent()).isTrue();
    }

    @Test
    void enabledGatewaySendsReactSetupEmail() {
        given(authProperties.enabled()).willReturn(true);
        given(identityProviderClient.canSendSetupEmail()).willReturn(true);
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);
        CreateAccountResponse response = service.createAccount(
                ADMIN, request("kitchen.c", "kitchen.c@example.test", CareNestRole.KITCHEN_STAFF, null));
        verify(authGatewayClient).sendReactSetupEmail(KC_ID);
        verify(identityProviderClient, never()).sendSetupEmail(anyString());
        assertThat(response.setupEmailSent()).isTrue();
    }

    @Test
    void requiresTemporaryPasswordWhenNoSetupEmailCanBeSent() {
        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("kitchen.c", null, CareNestRole.KITCHEN_STAFF, null)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.BAD_REQUEST.getHttpStatus());
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    void refusesToCreateAdminAccounts() {
        assertThatThrownBy(
                        () -> service.createAccount(ADMIN, request("admin.2", null, CareNestRole.ADMIN, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .hasMessage(ApiCode.ROLE_NOT_CREATABLE.getDefaultDesc());
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    @DisplayName("AUTH-18: tài khoản phụ huynh chỉ tạo qua luồng gắn trẻ, không qua POST /accounts")
    void refusesToCreateParentWithoutChild() {
        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("0912345678", null, CareNestRole.PARENT, "Temp-Pass-1")))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ROLE_NOT_CREATABLE);
        verifyNoInteractions(identityProviderClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0912345678", "84912345678", "0912.345.678"})
    @DisplayName(
            "AUTH-18: tên đăng nhập nhân sự dạng SĐT ⇒ 400 trước khi gọi Keycloak (không trùng tên đăng nhập phụ huynh)")
    void rejectsPhoneShapedStaffUsername(String username) {
        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request(username, null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .hasMessage("Tên đăng nhập nhân sự không được là số điện thoại")
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_USERNAME_IS_PHONE);
        verifyNoInteractions(identityProviderClient);
        verify(userAccountRepository, never()).existsByUsername(anyString());
    }

    @Test
    @DisplayName("AUTH-15: tên đăng nhập có chữ số nhưng không phải SĐT ⇒ tạo như thường")
    void allowsUsernameThatOnlyLooksNumeric() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);

        CreateAccountResponse response =
                service.createAccount(ADMIN, request("0912345678a", null, CareNestRole.TEACHER, "Temp-Pass-1"));

        assertThat(response.userId()).isEqualTo(42L);
        verify(identityProviderClient).createUser(any());
    }

    @Test
    void rejectsExistingUsernameBeforeCallingKeycloak() {
        given(userAccountRepository.existsByUsername("teacher.a")).willReturn(true);

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.a", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.CONFLICT.getHttpStatus());
        verify(identityProviderClient, never()).createUser(any());
    }

    @Test
    void keycloakConflictBecomes409WithoutCompensation() {
        given(identityProviderClient.createUser(any()))
                .willThrow(new IdentityProviderException(IdentityProviderErrorReason.CONFLICT, "exists", null));

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.c", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.CONFLICT.getHttpStatus());
        verify(identityProviderClient, never()).deleteUser(anyString());
    }

    @Test
    void keycloakDownBecomes503() {
        given(identityProviderClient.createUser(any()))
                .willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null));

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.c", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.SERVICE_UNAVAILABLE.getHttpStatus());
    }

    @Test
    void roleAssignmentFailureDeletesCreatedKeycloakUser() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "role", null))
                .given(identityProviderClient)
                .assignRealmRole(KC_ID, "TEACHER");

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.c", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.SERVICE_UNAVAILABLE.getHttpStatus());
        verify(identityProviderClient).deleteUser(KC_ID);
        verify(userAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void databaseFailureDeletesCreatedKeycloakUser() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);
        willThrow(new DataIntegrityViolationException("duplicate"))
                .given(userAccountRepository)
                .saveAndFlush(any());

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.c", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.CONFLICT.getHttpStatus());
        verify(identityProviderClient).deleteUser(KC_ID);
    }

    @Test
    void failedCompensationStillReportsOriginalError() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);
        willThrow(new DataIntegrityViolationException("duplicate"))
                .given(userAccountRepository)
                .saveAndFlush(any());
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null))
                .given(identityProviderClient)
                .deleteUser(KC_ID);

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.c", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.CONFLICT.getHttpStatus());
    }

    @Test
    void setupEmailFailureKeepsCreatedAccount() {
        given(identityProviderClient.canSendSetupEmail()).willReturn(true);
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "smtp", null))
                .given(identityProviderClient)
                .sendSetupEmail(KC_ID);

        CreateAccountResponse response = service.createAccount(
                ADMIN, request("teacher.c", "teacher.c@example.test", CareNestRole.TEACHER, "Temp-Pass-1"));

        assertThat(response.setupEmailSent()).isFalse();
        verify(identityProviderClient, never()).deleteUser(anyString());
    }

    @Test
    void deactivationSavesStatusFirstThenDisablesAndLogsOutInKeycloak() {
        UserAccount account = existingAccount();
        given(userAccountRepository.findById(42L)).willReturn(Optional.of(account));

        AccountResponse response = service.updateAccountStatus(ADMIN, 42L, UserStatus.INACTIVE);

        InOrder order = inOrder(userAccountRepository, authState, identityProviderClient);
        order.verify(userAccountRepository).saveAndFlush(account);
        order.verify(authState).revokeAccountAuth(42L);
        order.verify(identityProviderClient).setUserEnabled(KC_ID, false);
        order.verify(identityProviderClient).logoutUser(KC_ID);
        assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void reactivationEnablesWithoutLogout() {
        UserAccount account = existingAccount();
        account.changeStatus(UserStatus.INACTIVE);
        given(userAccountRepository.findById(42L)).willReturn(Optional.of(account));

        service.updateAccountStatus(ADMIN, 42L, UserStatus.ACTIVE);

        verify(identityProviderClient).setUserEnabled(KC_ID, true);
        verify(identityProviderClient, never()).logoutUser(anyString());
    }

    @Test
    void keycloakSyncFailureReturns503ButCareNestStatusIsAlreadySaved() {
        UserAccount account = existingAccount();
        given(userAccountRepository.findById(42L)).willReturn(Optional.of(account));
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null))
                .given(identityProviderClient)
                .setUserEnabled(KC_ID, false);

        assertThatThrownBy(() -> service.updateAccountStatus(ADMIN, 42L, UserStatus.INACTIVE))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.SERVICE_UNAVAILABLE.getHttpStatus());
        verify(userAccountRepository).saveAndFlush(account);
        assertThat(account.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void unknownAccountReturns404() {
        given(userAccountRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAccountStatus(ADMIN, 99L, UserStatus.INACTIVE))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.NOT_FOUND.getHttpStatus());
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    void adminCannotChangeOwnAccountStatus() {
        assertThatThrownBy(() -> service.updateAccountStatus(ADMIN, ADMIN.userId(), UserStatus.INACTIVE))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.BAD_REQUEST.getHttpStatus());
        verifyNoInteractions(userAccountRepository, identityProviderClient);
    }

    private static CreateAccountRequest request(String username, String email, CareNestRole role, String password) {
        return new CreateAccountRequest(username, "Giáo viên Demo", email, role, password, null);
    }

    private static UserAccount existingAccount() {
        UserAccount account = UserAccount.create(KC_ID, "teacher.c", null, "Giáo viên Demo");
        ReflectionTestUtils.setField(account, "id", 42L);
        return account;
    }

    @Test
    void unexpectedDatabaseFailureStillDeletesCreatedKeycloakUser() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);
        willThrow(new DataAccessResourceFailureException("connection lost"))
                .given(userAccountRepository)
                .saveAndFlush(any());

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.c", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(identityProviderClient).deleteUser(KC_ID);
    }

    @Test
    void keycloakRejectingAccountDataBecomes400() {
        given(identityProviderClient.createUser(any()))
                .willThrow(
                        new IdentityProviderException(IdentityProviderErrorReason.REJECTED, "password policy", null));

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.c", null, CareNestRole.TEACHER, "teacher.c")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.BAD_REQUEST.getHttpStatus());
        verify(identityProviderClient, never()).deleteUser(anyString());
    }

    private static final CurrentUser PRINCIPAL =
            new CurrentUser(2L, "kc-p", "principal.dev", "HT", null, Set.of(CareNestRole.PRINCIPAL));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Test
    @DisplayName("AUTH-15: tạo GV kèm lớp ⇒ kiểm phạm vi theo campus của lớp, lưu phân công cùng tài khoản")
    void createsTeacherTogetherWithClassroomAssignment() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);
        given(staffAssignmentService.resolveCampusId(StaffRole.TEACHER, null, 11L))
                .willReturn(5L);
        StaffAssignmentResponse assignment =
                new StaffAssignmentResponse(7L, 42L, StaffRole.TEACHER, null, 11L, TODAY, null);
        given(staffAssignmentService.createAssignment(any())).willReturn(assignment);

        CreateAccountResponse response = service.createAccount(
                PRINCIPAL,
                new CreateAccountRequest(
                        "teacher.c",
                        "GV C",
                        null,
                        CareNestRole.TEACHER,
                        "Temp-Pass-1",
                        new StaffAssignmentScopeRequest(null, 11L, null)));

        verify(staffAccessPolicy).checkCanCreate(PRINCIPAL, CareNestRole.TEACHER, 5L);
        ArgumentCaptor<NewStaffAssignment> created = ArgumentCaptor.forClass(NewStaffAssignment.class);
        verify(staffAssignmentService).createAssignment(created.capture());
        assertThat(created.getValue())
                .isEqualTo(new NewStaffAssignment(42L, StaffRole.TEACHER, null, 11L, TODAY, null));
        assertThat(response.assignment()).isEqualTo(assignment);
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPrimaryRole()).isEqualTo(CareNestRole.TEACHER);
    }

    @Test
    @DisplayName("AUTH-01: tài khoản Hiệu trưởng tự có phân công toàn trường")
    void principalAccountGetsWholeSchoolAssignment() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);

        service.createAccount(ADMIN, request("principal.b", null, CareNestRole.PRINCIPAL, "Temp-Pass-1"));

        verify(staffAccessPolicy).checkCanCreate(ADMIN, CareNestRole.PRINCIPAL, null);
        verify(staffAssignmentService, never()).resolveCampusId(any(), any(), any());
        verify(staffAssignmentService)
                .createAssignment(new NewStaffAssignment(42L, StaffRole.PRINCIPAL, null, null, TODAY, null));
    }

    @Test
    @DisplayName("AUTH-15: người tạo ngoài phạm vi bị từ chối trước khi gọi Keycloak")
    void deniedCreatorNeverReachesKeycloak() {
        willThrow(GlobalException.of(ApiCode.ROLE_CREATE_DENIED))
                .given(staffAccessPolicy)
                .checkCanCreate(any(), any(), any());

        assertThatThrownBy(() -> service.createAccount(
                        PRINCIPAL, request("teacher.c", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ROLE_CREATE_DENIED);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    void principalRequestWithScopeIsRejected() {
        assertThatThrownBy(() -> service.createAccount(
                        ADMIN,
                        new CreateAccountRequest(
                                "principal.b",
                                "HT B",
                                null,
                                CareNestRole.PRINCIPAL,
                                "Temp-Pass-1",
                                new StaffAssignmentScopeRequest(3L, null, null))))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PRINCIPAL_ASSIGNMENT_NO_SCOPE);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    void assignmentFailureDeletesCreatedKeycloakUser() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);
        given(staffAssignmentService.resolveCampusId(StaffRole.KITCHEN_STAFF, 3L, null))
                .willReturn(3L);
        given(staffAssignmentService.createAssignment(any())).willThrow(GlobalException.of(ApiCode.ASSIGNMENT_OVERLAP));

        assertThatThrownBy(() -> service.createAccount(
                        PRINCIPAL,
                        new CreateAccountRequest(
                                "kitchen.c",
                                "Bếp C",
                                null,
                                CareNestRole.KITCHEN_STAFF,
                                "Temp-Pass-1",
                                new StaffAssignmentScopeRequest(3L, null, null))))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ASSIGNMENT_OVERLAP);
        verify(identityProviderClient).deleteUser(KC_ID);
    }

    @Test
    @DisplayName("AUTH-17: cấp lại mật khẩu tạm đặt mật khẩu Keycloak rồi hủy mọi phiên")
    void resetTemporaryPasswordRevokesSessions() {
        UserAccount teacher = UserAccount.create("kc-t", "teacher.a", null, "GV A", CareNestRole.TEACHER);
        ReflectionTestUtils.setField(teacher, "id", 42L);
        given(userAccountRepository.findById(42L)).willReturn(Optional.of(teacher));

        service.resetTemporaryPassword(PRINCIPAL, 42L, "New-Temp-1");

        verify(staffAccessPolicy).checkCanManageAccount(PRINCIPAL, 42L, CareNestRole.TEACHER);
        InOrder order = inOrder(identityProviderClient, authState);
        order.verify(identityProviderClient).resetTemporaryPassword("kc-t", "New-Temp-1");
        order.verify(authState).revokeAccountAuth(42L);
        order.verify(identityProviderClient).logoutUser("kc-t");
    }

    @Test
    @DisplayName("AUTH-12: không cấp lại mật khẩu tạm cho chính mình")
    void cannotResetOwnTemporaryPassword() {
        assertThatThrownBy(() -> service.resetTemporaryPassword(PRINCIPAL, 2L, "New-Temp-1"))
                .extracting("apiCode")
                .isEqualTo(ApiCode.USE_OWN_PASSWORD_CHANGE);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    void sessionRevokeFailureAfterResetReturns503() {
        UserAccount teacher = UserAccount.create("kc-t", "teacher.a", null, "GV A", CareNestRole.TEACHER);
        ReflectionTestUtils.setField(teacher, "id", 42L);
        given(userAccountRepository.findById(42L)).willReturn(Optional.of(teacher));
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null))
                .given(identityProviderClient)
                .logoutUser("kc-t");

        assertThatThrownBy(() -> service.resetTemporaryPassword(PRINCIPAL, 42L, "New-Temp-1"))
                .extracting("apiCode")
                .isEqualTo(ApiCode.SESSIONS_NOT_REVOKED);
    }
}
