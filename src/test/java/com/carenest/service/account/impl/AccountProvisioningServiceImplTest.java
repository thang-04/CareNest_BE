package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.config.AuthProperties;
import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.account.ChangeStaffRoleRequest;
import com.carenest.dto.account.CreateAccountRequest;
import com.carenest.dto.account.CreateAccountResponse;
import com.carenest.dto.account.StaffAssignmentScopeRequest;
import com.carenest.dto.account.UpdateAccountProfileRequest;
import com.carenest.dto.common.FieldErrorResponse;
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
import com.carenest.service.account.PermissionService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    private final GuardianChildLinkService guardianChildLinkService = mock(GuardianChildLinkService.class);
    private final EnrollmentService enrollmentService = mock(EnrollmentService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
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
            guardianChildLinkService,
            enrollmentService,
            permissionService,
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
                ADMIN, request(" +84 912 000 001 ", "Teacher.C@Example.TEST", CareNestRole.TEACHER, "Temp-Pass-1"));

        ArgumentCaptor<NewIdentityUser> identity = ArgumentCaptor.forClass(NewIdentityUser.class);
        InOrder order = inOrder(identityProviderClient, userAccountRepository);
        order.verify(identityProviderClient).createUser(identity.capture());
        order.verify(identityProviderClient).assignRealmRole(KC_ID, "TEACHER");
        order.verify(userAccountRepository).saveAndFlush(any());
        assertThat(identity.getValue().username()).isEqualTo("0912000001");
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
                ADMIN, request("0912000002", "kitchen.c@example.test", CareNestRole.KITCHEN_STAFF, null));

        verify(identityProviderClient).sendSetupEmail(KC_ID);
        assertThat(response.setupEmailSent()).isTrue();
    }

    @Test
    void enabledGatewaySendsReactSetupEmail() {
        given(authProperties.enabled()).willReturn(true);
        given(identityProviderClient.canSendSetupEmail()).willReturn(true);
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);
        CreateAccountResponse response = service.createAccount(
                ADMIN, request("0912000002", "kitchen.c@example.test", CareNestRole.KITCHEN_STAFF, null));
        verify(authGatewayClient).sendReactSetupEmail(KC_ID);
        verify(identityProviderClient, never()).sendSetupEmail(anyString());
        assertThat(response.setupEmailSent()).isTrue();
    }

    @Test
    void requiresTemporaryPasswordWhenNoSetupEmailCanBeSent() {
        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("0912000002", null, CareNestRole.KITCHEN_STAFF, null)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.BAD_REQUEST.getHttpStatus());
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    void refusesToCreateAdminAccounts() {
        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("0912000003", null, CareNestRole.ADMIN, "Temp-Pass-1")))
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

    @Test
    void rejectsExistingUsernameBeforeCallingKeycloak() {
        UserAccount staff = UserAccount.create("kc-a", "0912000004", null, "GV A", CareNestRole.TEACHER);
        ReflectionTestUtils.setField(staff, "id", 44L);
        given(userAccountRepository.findByUsername("0912000004")).willReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.createAccount(
                        ADMIN, request("0912000004", "teacher.c@example.test", CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.CONFLICT.getHttpStatus());
        verify(identityProviderClient, never()).createUser(any());
    }

    @Test
    void keycloakConflictBecomes409WithoutCompensation() {
        given(identityProviderClient.createUser(any()))
                .willThrow(new IdentityProviderException(IdentityProviderErrorReason.CONFLICT, "exists", null));

        assertThatThrownBy(() -> service.createAccount(
                        ADMIN, request("0912000001", "teacher.c@example.test", CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.CONFLICT.getHttpStatus());
        verify(identityProviderClient, never()).deleteUser(anyString());
    }

    @Test
    void keycloakDownBecomes503() {
        given(identityProviderClient.createUser(any()))
                .willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null));

        assertThatThrownBy(() -> service.createAccount(
                        ADMIN, request("0912000001", "teacher.c@example.test", CareNestRole.TEACHER, "Temp-Pass-1")))
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

        assertThatThrownBy(() -> service.createAccount(
                        ADMIN, request("0912000001", "teacher.c@example.test", CareNestRole.TEACHER, "Temp-Pass-1")))
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

        assertThatThrownBy(() -> service.createAccount(
                        ADMIN, request("0912000001", "teacher.c@example.test", CareNestRole.TEACHER, "Temp-Pass-1")))
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

        assertThatThrownBy(() -> service.createAccount(
                        ADMIN, request("0912000001", "teacher.c@example.test", CareNestRole.TEACHER, "Temp-Pass-1")))
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
                ADMIN, request("0912000001", "teacher.c@example.test", CareNestRole.TEACHER, "Temp-Pass-1"));

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
        return new CreateAccountRequest(username, "Giáo viên Demo", email, role, password, null, null);
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

        assertThatThrownBy(() -> service.createAccount(
                        ADMIN, request("0912000001", "teacher.c@example.test", CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(identityProviderClient).deleteUser(KC_ID);
    }

    @Test
    void keycloakRejectingAccountDataBecomes400() {
        given(identityProviderClient.createUser(any()))
                .willThrow(
                        new IdentityProviderException(IdentityProviderErrorReason.REJECTED, "password policy", null));

        assertThatThrownBy(() -> service.createAccount(
                        ADMIN, request("0912000001", "teacher.c@example.test", CareNestRole.TEACHER, "teacher.c")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode().getHttpStatus())
                .isEqualTo(ApiCode.BAD_REQUEST.getHttpStatus());
        verify(identityProviderClient, never()).deleteUser(anyString());
    }

    private static final CurrentUser PRINCIPAL =
            new CurrentUser(2L, "kc-p", "principal.dev", "HT", null, Set.of(CareNestRole.PRINCIPAL));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Test
    @DisplayName("AUTH-15: tạo PHT, GV thiếu email ⇒ 400 gắn ô email, chưa đụng Keycloak")
    void rejectsViceOrTeacherWithoutEmail() {
        for (CareNestRole role : List.of(CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER)) {
            assertThatThrownBy(() -> service.createAccount(
                            PRINCIPAL,
                            new CreateAccountRequest(
                                    "0912000001",
                                    "Nhân sự C",
                                    " ",
                                    role,
                                    "Temp-Pass-1",
                                    new StaffAssignmentScopeRequest(5L, null),
                                    null)))
                    .isInstanceOfSatisfying(GlobalException.class, ex -> {
                        assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                        assertThat(ex.getFieldErrors())
                                .extracting(FieldErrorResponse::field)
                                .containsExactly("email");
                    });
        }
        verify(identityProviderClient, never()).createUser(any());
    }

    @Test
    @DisplayName("AUTH-15: tạo GV không kèm lớp ⇒ campus chỉ dùng kiểm quyền người tạo, không tạo phân công")
    void createsTeacherWithoutClassroomAssignment() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);

        CreateAccountResponse response = service.createAccount(
                PRINCIPAL,
                new CreateAccountRequest(
                        "0912000001",
                        "GV C",
                        "gv.c@carenest.test",
                        CareNestRole.TEACHER,
                        "Temp-Pass-1",
                        new StaffAssignmentScopeRequest(5L, null),
                        null));

        verify(staffAccessPolicy).checkCanCreate(PRINCIPAL, CareNestRole.TEACHER, 5L);
        verify(staffAssignmentService, never()).resolveCampusId(any(), any(), any());
        verify(staffAssignmentService, never()).createAssignment(any());
        assertThat(response.assignment()).isNull();
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPrimaryRole()).isEqualTo(CareNestRole.TEACHER);
    }

    @Test
    @DisplayName("AUTH-01: tài khoản Hiệu trưởng tự có phân công toàn trường")
    void principalAccountGetsWholeSchoolAssignment() {
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);

        service.createAccount(ADMIN, request("0912000005", null, CareNestRole.PRINCIPAL, "Temp-Pass-1"));

        verify(staffAccessPolicy).checkCanCreate(ADMIN, CareNestRole.PRINCIPAL, null);
        verify(staffAssignmentService, never()).resolveCampusId(any(), any(), any());
        verify(staffAssignmentService)
                .createAssignment(new NewStaffAssignment(
                        42L, StaffRole.PRINCIPAL, null, null, TODAY, null, ADMIN.userId(), false));
    }

    @Test
    @DisplayName("AUTH-15: người tạo ngoài phạm vi bị từ chối trước khi gọi Keycloak")
    void deniedCreatorNeverReachesKeycloak() {
        willThrow(GlobalException.of(ApiCode.ACCOUNT_ROLE_CREATE_DENIED))
                .given(staffAccessPolicy)
                .checkCanCreate(any(), any(), any());

        assertThatThrownBy(() -> service.createAccount(
                        PRINCIPAL,
                        request("0912000001", "teacher.c@example.test", CareNestRole.TEACHER, "Temp-Pass-1")))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ACCOUNT_ROLE_CREATE_DENIED);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    void principalRequestWithScopeIsRejected() {
        assertThatThrownBy(() -> service.createAccount(
                        ADMIN,
                        new CreateAccountRequest(
                                "0912000005",
                                "HT B",
                                null,
                                CareNestRole.PRINCIPAL,
                                "Temp-Pass-1",
                                new StaffAssignmentScopeRequest(3L, null),
                                null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PRINCIPAL_ASSIGNMENT_SCHOOL_WIDE);
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
                                "0912000002",
                                "Bếp C",
                                null,
                                CareNestRole.KITCHEN_STAFF,
                                "Temp-Pass-1",
                                new StaffAssignmentScopeRequest(3L, null),
                                null)))
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
                .isEqualTo(ApiCode.USE_CHANGE_PASSWORD_FOR_SELF);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    @DisplayName("GV nghỉ việc nhưng con vẫn học: gỡ role GV, kết thúc phân công, giữ tài khoản phụ huynh, hủy phiên")
    void revokeStaffRoleKeepsParentAccount() {
        UserAccount teacher = UserAccount.create("kc-t", "0912345678", null, "GV A", CareNestRole.TEACHER);
        ReflectionTestUtils.setField(teacher, "id", 42L);
        given(userAccountRepository.findById(42L)).willReturn(Optional.of(teacher));
        given(guardianChildLinkService.findChildIdsOfGuardian(42L)).willReturn(List.of(101L));
        given(staffAssignmentService.findAssignments(42L))
                .willReturn(List.of(
                        new StaffAssignmentResponse(
                                7L, 42L, StaffRole.TEACHER, null, 11L, LocalDate.of(2026, 9, 1), null, false),
                        new StaffAssignmentResponse(
                                8L, 42L, StaffRole.TEACHER, null, 12L, LocalDate.of(2026, 11, 1), null, false),
                        new StaffAssignmentResponse(
                                6L,
                                42L,
                                StaffRole.TEACHER,
                                null,
                                10L,
                                LocalDate.of(2025, 9, 1),
                                LocalDate.of(2026, 5, 31),
                                false)));

        service.revokeStaffRole(PRINCIPAL, 42L);

        verify(staffAccessPolicy).checkCanManageAccount(PRINCIPAL, 42L, CareNestRole.TEACHER);
        InOrder order = inOrder(identityProviderClient, staffAssignmentService, userAccountRepository, authState);
        order.verify(identityProviderClient).removeRealmRole("kc-t", "TEACHER");
        order.verify(staffAssignmentService).endAssignment(7L, LocalDate.of(2026, 10, 17));
        order.verify(staffAssignmentService).endAssignment(8L, LocalDate.of(2026, 11, 1));
        order.verify(userAccountRepository).updatePrimaryRole(42L, "PARENT");
        order.verify(authState).revokeAccountAuth(42L);
        order.verify(identityProviderClient).logoutUser("kc-t");
        verify(staffAssignmentService, never()).endAssignment(eq(6L), any());
        verify(permissionService).clearPermissionOverrides(eq(PRINCIPAL.userId()), eq(42L), anyString());
        assertThat(teacher.getPrimaryRole()).isEqualTo(CareNestRole.PARENT);
    }

    @Test
    @DisplayName("AUTH-19: PHT không gộp được phụ huynh không có con ở campus mình thành nhân sự")
    void vicePrincipalCannotMergeParentOfAnotherCampus() {
        UserAccount parent = UserAccount.create("kc-p", "0912000006", null, "PH", CareNestRole.PARENT);
        ReflectionTestUtils.setField(parent, "id", 46L);
        given(userAccountRepository.findByUsername("0912000006")).willReturn(Optional.of(parent));
        willThrow(GlobalException.of(ApiCode.ACCOUNT_MANAGE_DENIED))
                .given(staffAccessPolicy)
                .checkCanManageParent(PRINCIPAL, 46L);

        assertThatThrownBy(() -> service.createAccount(
                        PRINCIPAL,
                        new CreateAccountRequest("0912000006", "PH", null, CareNestRole.TEACHER, null, null, 46L)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ACCOUNT_MANAGE_DENIED);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    @DisplayName("Gộp phụ huynh thành nhân sự lỗi ở DB ⇒ gỡ role Keycloak vừa thêm")
    void mergeFailureRemovesGrantedRole() {
        UserAccount parent = UserAccount.create("kc-p", "0912000006", null, "PH", CareNestRole.PARENT);
        ReflectionTestUtils.setField(parent, "id", 46L);
        given(userAccountRepository.findByUsername("0912000006")).willReturn(Optional.of(parent));
        given(identityProviderClient.getRealmRoleNames("kc-p")).willReturn(List.of("PARENT"));
        given(staffAssignmentService.resolveCampusId(StaffRole.KITCHEN_STAFF, 3L, null))
                .willReturn(3L);
        given(staffAssignmentService.createAssignment(any())).willThrow(GlobalException.of(ApiCode.ASSIGNMENT_OVERLAP));

        assertThatThrownBy(() -> service.createAccount(
                        PRINCIPAL,
                        new CreateAccountRequest(
                                "0912000006",
                                "PH",
                                null,
                                CareNestRole.KITCHEN_STAFF,
                                null,
                                new StaffAssignmentScopeRequest(3L, null),
                                46L)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ASSIGNMENT_OVERLAP);
        verify(identityProviderClient).removeRealmRole("kc-p", "KITCHEN_STAFF");
        assertThat(parent.getPrimaryRole()).isEqualTo(CareNestRole.PARENT);
    }

    @Test
    @DisplayName("AUTH-19: cấp lại mật khẩu nhân sự kiêm phụ huynh cần quản được phụ huynh đó")
    void resetPasswordOfStaffParentChecksParentScope() {
        UserAccount teacher = UserAccount.create("kc-t", "0912000001", null, "GV A", CareNestRole.TEACHER);
        ReflectionTestUtils.setField(teacher, "id", 42L);
        given(userAccountRepository.findById(42L)).willReturn(Optional.of(teacher));
        given(guardianChildLinkService.findChildIdsOfGuardian(42L)).willReturn(List.of(101L));
        willThrow(GlobalException.of(ApiCode.ACCOUNT_MANAGE_DENIED))
                .given(staffAccessPolicy)
                .checkCanManageParent(PRINCIPAL, 42L);

        assertThatThrownBy(() -> service.resetTemporaryPassword(PRINCIPAL, 42L, "New-Temp-1"))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ACCOUNT_MANAGE_DENIED);
        verify(identityProviderClient, never()).resetTemporaryPassword(any(), any());
    }

    @Test
    @DisplayName("CHD-04: mọi con đã ra trường ⇒ không thu hồi, dùng khóa tài khoản")
    void revokeStaffRoleRequiresStudyingChild() {
        UserAccount teacher = UserAccount.create("kc-t", "0912000001", null, "GV A", CareNestRole.TEACHER);
        ReflectionTestUtils.setField(teacher, "id", 42L);
        given(userAccountRepository.findById(42L)).willReturn(Optional.of(teacher));
        given(guardianChildLinkService.findChildIdsOfGuardian(42L)).willReturn(List.of(101L));
        given(enrollmentService.findChildIdsLeftSchool(List.of(101L), LocalDate.of(2026, 10, 17)))
                .willReturn(Set.of(101L));

        assertThatThrownBy(() -> service.revokeStaffRole(PRINCIPAL, 42L))
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_ROLE_REVOKE_REQUIRES_CHILD);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    @DisplayName("Không còn liên kết trẻ ⇒ không thu hồi, dùng khóa tài khoản")
    void revokeStaffRoleRequiresLinkedChild() {
        UserAccount teacher = UserAccount.create("kc-t", "teacher.a", null, "GV A", CareNestRole.TEACHER);
        ReflectionTestUtils.setField(teacher, "id", 42L);
        given(userAccountRepository.findById(42L)).willReturn(Optional.of(teacher));
        given(guardianChildLinkService.findChildIdsOfGuardian(42L)).willReturn(List.of());

        assertThatThrownBy(() -> service.revokeStaffRole(PRINCIPAL, 42L))
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_ROLE_REVOKE_REQUIRES_CHILD);
        verifyNoInteractions(identityProviderClient);
        assertThat(teacher.getPrimaryRole()).isEqualTo(CareNestRole.TEACHER);
    }

    @Test
    void revokeStaffRoleRejectsParentOnlyAndSelf() {
        UserAccount parent = UserAccount.create("kc-p", "0912345679", null, "PH", CareNestRole.PARENT);
        ReflectionTestUtils.setField(parent, "id", 43L);
        given(userAccountRepository.findById(43L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.revokeStaffRole(PRINCIPAL, 43L))
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_ROLE_NOT_REVOCABLE);
        assertThatThrownBy(() -> service.revokeStaffRole(PRINCIPAL, PRINCIPAL.userId()))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CANNOT_MANAGE_OWN_ACCOUNT);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    @DisplayName("Keycloak lỗi khi gỡ role ⇒ DB không đổi, gọi lại được")
    void revokeStaffRoleKeepsDatabaseWhenKeycloakFails() {
        UserAccount teacher = UserAccount.create("kc-t", "0912345678", null, "GV A", CareNestRole.TEACHER);
        ReflectionTestUtils.setField(teacher, "id", 42L);
        given(userAccountRepository.findById(42L)).willReturn(Optional.of(teacher));
        given(guardianChildLinkService.findChildIdsOfGuardian(42L)).willReturn(List.of(101L));
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null))
                .given(identityProviderClient)
                .removeRealmRole("kc-t", "TEACHER");

        assertThatThrownBy(() -> service.revokeStaffRole(PRINCIPAL, 42L))
                .extracting("apiCode")
                .isEqualTo(ApiCode.IDENTITY_PROVIDER_UNAVAILABLE);
        verify(staffAssignmentService, never()).endAssignment(any(), any());
        verify(userAccountRepository, never()).updatePrimaryRole(any(), any());
        assertThat(teacher.getPrimaryRole()).isEqualTo(CareNestRole.TEACHER);
    }

    @Test
    @DisplayName("Tạo nhân sự: SĐT sai định dạng ⇒ 400 trước khi gọi Keycloak")
    void invalidStaffPhoneIsRejected() {
        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.c", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .extracting("apiCode")
                .isEqualTo(ApiCode.PHONE_NUMBER_INVALID);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    @DisplayName(
            "Phụ huynh trở thành GV: cùng tài khoản, thêm role, chưa phân công lớp, giữ SĐT/mật khẩu, không tạo user")
    void parentAccountBecomesTeacher() {
        UserAccount parent = UserAccount.create("kc-p", "0912000006", null, "Nguyễn Thị Lan", CareNestRole.PARENT);
        ReflectionTestUtils.setField(parent, "id", 46L);
        given(userAccountRepository.findByUsername("0912000006")).willReturn(Optional.of(parent));
        given(identityProviderClient.getRealmRoleNames("kc-p")).willReturn(List.of("PARENT"));

        CreateAccountResponse response = service.createAccount(
                PRINCIPAL,
                new CreateAccountRequest(
                        "0912 000 006",
                        "Nguyễn Thị Lan",
                        null,
                        CareNestRole.TEACHER,
                        null,
                        new StaffAssignmentScopeRequest(5L, null),
                        46L));

        verify(staffAccessPolicy).checkCanCreate(PRINCIPAL, CareNestRole.TEACHER, 5L);
        verify(staffAccessPolicy).checkCanManageParent(PRINCIPAL, 46L);
        InOrder order = inOrder(identityProviderClient, userAccountRepository);
        order.verify(identityProviderClient).assignRealmRole("kc-p", "TEACHER");
        order.verify(userAccountRepository).updatePrimaryRole(46L, "TEACHER");
        verify(staffAssignmentService, never()).createAssignment(any());
        verify(identityProviderClient, never()).createUser(any());
        verify(identityProviderClient, never()).resetTemporaryPassword(any(), any());
        verify(permissionService).clearPermissionOverrides(eq(PRINCIPAL.userId()), eq(46L), anyString());
        assertThat(parent.getPrimaryRole()).isEqualTo(CareNestRole.TEACHER);
        assertThat(response.assignment()).isNull();
    }

    @Test
    @DisplayName(
            "AUTH-15: Admin tạo Hiệu trưởng từ SĐT đã là phụ huynh (đã xác nhận) ⇒ thêm vai trò, không kiểm campus")
    void adminTurnsConfirmedParentIntoPrincipal() {
        UserAccount parent = UserAccount.create("kc-p", "0912000006", null, "PH", CareNestRole.PARENT);
        ReflectionTestUtils.setField(parent, "id", 46L);
        given(userAccountRepository.findByUsername("0912000006")).willReturn(Optional.of(parent));
        given(identityProviderClient.getRealmRoleNames("kc-p")).willReturn(List.of("PARENT"));

        service.createAccount(
                ADMIN, new CreateAccountRequest("0912000006", "PH", null, CareNestRole.PRINCIPAL, null, null, 46L));

        verify(staffAccessPolicy, never()).checkCanManageParent(any(), any());
        verify(identityProviderClient).assignRealmRole("kc-p", "PRINCIPAL");
        verify(userAccountRepository).updatePrimaryRole(46L, "PRINCIPAL");
        assertThat(parent.getPrimaryRole()).isEqualTo(CareNestRole.PRINCIPAL);
    }

    private static final CurrentUser VICE_PRINCIPAL =
            new CurrentUser(3L, "kc-vp", "0912000003", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL));

    private UserAccount givenStaff(Long id, CareNestRole role) {
        UserAccount account = UserAccount.create("kc-s", "0912000042", null, "Nhân sự A", role);
        ReflectionTestUtils.setField(account, "id", id);
        given(userAccountRepository.findById(id)).willReturn(Optional.of(account));
        return account;
    }

    @Test
    @DisplayName("AUTH-25: HT đổi GV sang bếp ⇒ thêm role mới, gỡ role cũ, kết thúc phân công, xóa ngoại lệ, "
            + "phân công campus mới, hủy phiên")
    void principalChangesTeacherToKitchenStaff() {
        UserAccount teacher = givenStaff(42L, CareNestRole.TEACHER);
        given(identityProviderClient.getRealmRoleNames("kc-s")).willReturn(List.of("TEACHER"));
        given(staffAssignmentService.resolveCampusId(StaffRole.KITCHEN_STAFF, 3L, null))
                .willReturn(3L);
        given(staffAssignmentService.findAssignments(42L))
                .willReturn(List.of(new StaffAssignmentResponse(
                        7L, 42L, StaffRole.TEACHER, null, 11L, LocalDate.of(2026, 9, 1), null, false)));

        service.changeStaffRole(
                PRINCIPAL,
                42L,
                new ChangeStaffRoleRequest(CareNestRole.KITCHEN_STAFF, new StaffAssignmentScopeRequest(3L, null)));

        verify(staffAccessPolicy).checkCanManageAccount(PRINCIPAL, 42L, CareNestRole.TEACHER);
        verify(staffAccessPolicy).checkCanCreate(PRINCIPAL, CareNestRole.KITCHEN_STAFF, 3L);
        InOrder order = inOrder(
                identityProviderClient, staffAssignmentService, permissionService, userAccountRepository, authState);
        order.verify(identityProviderClient).assignRealmRole("kc-s", "KITCHEN_STAFF");
        order.verify(identityProviderClient).removeRealmRole("kc-s", "TEACHER");
        order.verify(staffAssignmentService).endAssignment(7L, TODAY);
        order.verify(permissionService).clearPermissionOverrides(eq(PRINCIPAL.userId()), eq(42L), anyString());
        order.verify(userAccountRepository).updatePrimaryRole(42L, "KITCHEN_STAFF");
        ArgumentCaptor<NewStaffAssignment> assignment = ArgumentCaptor.forClass(NewStaffAssignment.class);
        order.verify(staffAssignmentService).createAssignment(assignment.capture());
        order.verify(authState).revokeAccountAuth(42L);
        order.verify(identityProviderClient).logoutUser("kc-s");
        assertThat(assignment.getValue().staffRole()).isEqualTo(StaffRole.KITCHEN_STAFF);
        assertThat(assignment.getValue().campusId()).isEqualTo(3L);
        assertThat(assignment.getValue().validFrom()).isEqualTo(TODAY);
        assertThat(assignment.getValue().assignedBy()).isEqualTo(PRINCIPAL.userId());
        assertThat(teacher.getPrimaryRole()).isEqualTo(CareNestRole.KITCHEN_STAFF);
    }

    @Test
    @DisplayName("AUTH-25: đổi sang GV không tạo phân công lớp (AUTH-15); campus chỉ để kiểm PHT")
    void changeToTeacherCreatesNoAssignment() {
        givenStaff(42L, CareNestRole.KITCHEN_STAFF);
        given(identityProviderClient.getRealmRoleNames("kc-s")).willReturn(List.of("KITCHEN_STAFF"));

        service.changeStaffRole(
                VICE_PRINCIPAL,
                42L,
                new ChangeStaffRoleRequest(CareNestRole.TEACHER, new StaffAssignmentScopeRequest(5L, null)));

        verify(staffAccessPolicy).checkCanCreate(VICE_PRINCIPAL, CareNestRole.TEACHER, 5L);
        verify(staffAssignmentService, never()).createAssignment(any());
        verify(userAccountRepository).updatePrimaryRole(42L, "TEACHER");
    }

    @Test
    @DisplayName("AUTH-25: PHT không được đổi người sang PHT ⇒ 403 trước khi gọi Keycloak")
    void vicePrincipalCannotPromoteToVicePrincipal() {
        givenStaff(42L, CareNestRole.TEACHER);
        given(staffAssignmentService.resolveCampusId(StaffRole.VICE_PRINCIPAL, 5L, null))
                .willReturn(5L);
        willThrow(GlobalException.of(ApiCode.ACCOUNT_ROLE_CREATE_DENIED))
                .given(staffAccessPolicy)
                .checkCanCreate(VICE_PRINCIPAL, CareNestRole.VICE_PRINCIPAL, 5L);

        assertThatThrownBy(() -> service.changeStaffRole(
                        VICE_PRINCIPAL,
                        42L,
                        new ChangeStaffRoleRequest(
                                CareNestRole.VICE_PRINCIPAL, new StaffAssignmentScopeRequest(5L, null))))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ACCOUNT_ROLE_CREATE_DENIED);
        verifyNoInteractions(identityProviderClient, permissionService);
    }

    @Test
    @DisplayName("AUTH-17: đổi vai trò người ngoài phạm vi quản lý ⇒ 403")
    void changeRoleOutsideScopeIsDenied() {
        givenStaff(42L, CareNestRole.TEACHER);
        willThrow(GlobalException.of(ApiCode.ACCOUNT_MANAGE_DENIED))
                .given(staffAccessPolicy)
                .checkCanManageAccount(VICE_PRINCIPAL, 42L, CareNestRole.TEACHER);

        assertThatThrownBy(() -> service.changeStaffRole(
                        VICE_PRINCIPAL, 42L, new ChangeStaffRoleRequest(CareNestRole.KITCHEN_STAFF, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ACCOUNT_MANAGE_DENIED);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    @DisplayName("AUTH-25: không tự đổi, không đổi sang/từ Hiệu trưởng, không đổi sang role đang có, không đổi TK khóa")
    void changeStaffRoleRejectsInvalidTargets() {
        UserAccount teacher = givenStaff(42L, CareNestRole.TEACHER);
        UserAccount principal = UserAccount.create("kc-h", "0912000044", null, "HT", CareNestRole.PRINCIPAL);
        ReflectionTestUtils.setField(principal, "id", 44L);
        given(userAccountRepository.findById(44L)).willReturn(Optional.of(principal));

        assertThatThrownBy(() -> service.changeStaffRole(
                        PRINCIPAL, PRINCIPAL.userId(), new ChangeStaffRoleRequest(CareNestRole.TEACHER, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.CANNOT_MANAGE_OWN_ACCOUNT);
        assertThatThrownBy(() -> service.changeStaffRole(
                        PRINCIPAL, 42L, new ChangeStaffRoleRequest(CareNestRole.PRINCIPAL, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_ROLE_NOT_CHANGEABLE);
        assertThatThrownBy(() ->
                        service.changeStaffRole(ADMIN, 44L, new ChangeStaffRoleRequest(CareNestRole.TEACHER, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_ROLE_NOT_CHANGEABLE);
        assertThatThrownBy(() ->
                        service.changeStaffRole(PRINCIPAL, 42L, new ChangeStaffRoleRequest(CareNestRole.TEACHER, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_ROLE_UNCHANGED);
        teacher.changeStatus(UserStatus.INACTIVE);
        assertThatThrownBy(() -> service.changeStaffRole(
                        PRINCIPAL, 42L, new ChangeStaffRoleRequest(CareNestRole.KITCHEN_STAFF, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.STAFF_ACCOUNT_LOCKED);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    @DisplayName("AUTH-25: Keycloak lỗi khi gỡ role cũ ⇒ gỡ role mới vừa thêm, DB không đổi")
    void changeStaffRoleCompensatesWhenRemovingOldRoleFails() {
        UserAccount teacher = givenStaff(42L, CareNestRole.TEACHER);
        given(identityProviderClient.getRealmRoleNames("kc-s")).willReturn(List.of("TEACHER"));
        willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null))
                .given(identityProviderClient)
                .removeRealmRole("kc-s", "TEACHER");

        assertThatThrownBy(() -> service.changeStaffRole(
                        PRINCIPAL, 42L, new ChangeStaffRoleRequest(CareNestRole.KITCHEN_STAFF, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.IDENTITY_PROVIDER_UNAVAILABLE);
        verify(identityProviderClient).removeRealmRole("kc-s", "KITCHEN_STAFF");
        verify(userAccountRepository, never()).updatePrimaryRole(any(), any());
        verifyNoInteractions(permissionService);
        assertThat(teacher.getPrimaryRole()).isEqualTo(CareNestRole.TEACHER);
    }

    @Test
    @DisplayName("AUTH-25: DB lỗi khi đổi vai trò ⇒ Keycloak trả về role cũ, không hủy phiên")
    void changeStaffRoleRestoresKeycloakWhenDatabaseFails() {
        UserAccount teacher = givenStaff(42L, CareNestRole.TEACHER);
        given(identityProviderClient.getRealmRoleNames("kc-s")).willReturn(List.of("TEACHER"));
        given(staffAssignmentService.resolveCampusId(StaffRole.KITCHEN_STAFF, 3L, null))
                .willReturn(3L);
        given(staffAssignmentService.createAssignment(any())).willThrow(GlobalException.of(ApiCode.ASSIGNMENT_OVERLAP));

        assertThatThrownBy(() -> service.changeStaffRole(
                        PRINCIPAL,
                        42L,
                        new ChangeStaffRoleRequest(
                                CareNestRole.KITCHEN_STAFF, new StaffAssignmentScopeRequest(3L, null))))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ASSIGNMENT_OVERLAP);
        verify(identityProviderClient).assignRealmRole("kc-s", "TEACHER");
        verify(identityProviderClient).removeRealmRole("kc-s", "KITCHEN_STAFF");
        verify(authState, never()).revokeAccountAuth(any());
        assertThat(teacher.getPrimaryRole()).isEqualTo(CareNestRole.TEACHER);
    }

    @Test
    @DisplayName("AUTH-25: sửa họ tên/email nhân sự ⇒ Keycloak rồi DB; email trống giữ email cũ")
    void updateProfileSyncsKeycloakThenDatabase() {
        UserAccount teacher = givenStaff(42L, CareNestRole.TEACHER);
        ReflectionTestUtils.setField(teacher, "email", "old@example.test");

        AccountResponse response = service.updateProfile(
                PRINCIPAL, 42L, new UpdateAccountProfileRequest("  Nguyễn Văn B ", " New@Example.TEST "));

        InOrder order = inOrder(identityProviderClient, userAccountRepository);
        order.verify(identityProviderClient).updateUserProfile("kc-s", "Nguyễn Văn B", "new@example.test");
        order.verify(userAccountRepository).saveAndFlush(teacher);
        assertThat(response.fullName()).isEqualTo("Nguyễn Văn B");
        assertThat(response.email()).isEqualTo("new@example.test");

        service.updateProfile(PRINCIPAL, 42L, new UpdateAccountProfileRequest("Nguyễn Văn C", " "));
        verify(identityProviderClient).updateUserProfile("kc-s", "Nguyễn Văn C", null);
        assertThat(teacher.getEmail()).isEqualTo("new@example.test");
    }

    @Test
    @DisplayName("AUTH-25: DB lỗi khi sửa hồ sơ ⇒ trả Keycloak về họ tên/email cũ")
    void updateProfileRestoresKeycloakWhenDatabaseFails() {
        UserAccount teacher = givenStaff(42L, CareNestRole.TEACHER);
        ReflectionTestUtils.setField(teacher, "email", "old@example.test");
        willThrow(new DataAccessResourceFailureException("connection lost"))
                .given(userAccountRepository)
                .saveAndFlush(teacher);

        assertThatThrownBy(() -> service.updateProfile(
                        PRINCIPAL, 42L, new UpdateAccountProfileRequest("Tên mới", "new@example.test")))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(identityProviderClient).updateUserProfile("kc-s", "Nhân sự A", "old@example.test");
    }

    @Test
    @DisplayName("AUTH-25, AUTH-19: không sửa hồ sơ phụ huynh ở đây; nhân sự kiêm phụ huynh cần quản được phụ huynh")
    void updateProfileChecksTargetKindAndParentScope() {
        UserAccount parent = UserAccount.create("kc-p", "0912000046", null, "PH", CareNestRole.PARENT);
        ReflectionTestUtils.setField(parent, "id", 46L);
        given(userAccountRepository.findById(46L)).willReturn(Optional.of(parent));
        givenStaff(42L, CareNestRole.TEACHER);
        given(guardianChildLinkService.findChildIdsOfGuardian(42L)).willReturn(List.of(101L));
        willThrow(GlobalException.of(ApiCode.ACCOUNT_MANAGE_DENIED))
                .given(staffAccessPolicy)
                .checkCanManageParent(VICE_PRINCIPAL, 42L);

        assertThatThrownBy(() -> service.updateProfile(ADMIN, 46L, new UpdateAccountProfileRequest("PH", null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ACCOUNT_PROFILE_NOT_EDITABLE);
        assertThatThrownBy(() -> service.updateProfile(
                        VICE_PRINCIPAL, 42L, new UpdateAccountProfileRequest("GV", "vp@example.test")))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ACCOUNT_MANAGE_DENIED);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    @DisplayName("SĐT đã là phụ huynh mà chưa xác nhận đúng người ⇒ 409, không thêm role")
    void parentAccountNeedsConfirmationBeforeBecomingStaff() {
        UserAccount parent = UserAccount.create("kc-p", "0912000006", null, "PH", CareNestRole.PARENT);
        ReflectionTestUtils.setField(parent, "id", 46L);
        given(userAccountRepository.findByUsername("0912000006")).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.createAccount(ADMIN, request("0912000006", null, CareNestRole.TEACHER, null)))
                .extracting("apiCode")
                .isEqualTo(ApiCode.ACCOUNT_CONFIRMATION_REQUIRED);
        verifyNoInteractions(identityProviderClient);
        assertThat(parent.getPrimaryRole()).isEqualTo(CareNestRole.PARENT);
    }

    @Test
    void accountCandidateMasksNameAndShowsRole() {
        UserAccount parent = UserAccount.create("kc-p", "0912000006", null, "Nguyễn Thị Lan", CareNestRole.PARENT);
        ReflectionTestUtils.setField(parent, "id", 46L);
        given(userAccountRepository.findByUsername("0912000006")).willReturn(Optional.of(parent));

        var candidate = service.findAccountCandidate(PRINCIPAL, "+84 912 000 006");

        assertThat(candidate.exists()).isTrue();
        assertThat(candidate.userId()).isEqualTo(46L);
        assertThat(candidate.maskedFullName()).isEqualTo("N***** T** L**");
        assertThat(candidate.primaryRole()).isEqualTo(CareNestRole.PARENT);
        assertThat(service.findAccountCandidate(PRINCIPAL, "0912000007").exists())
                .isFalse();
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
                .isEqualTo(ApiCode.PASSWORD_RESET_SESSION_REVOKE_FAILED);
    }
}
