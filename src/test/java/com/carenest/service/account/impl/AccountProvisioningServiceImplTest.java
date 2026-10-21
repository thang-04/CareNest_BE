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
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.AuthGatewayClient;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.integration.keycloak.NewIdentityUser;
import com.carenest.mapper.account.AccountMapper;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AuthStateStore;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.IdentityProviderErrorReason;
import com.carenest.utils.enums.UserStatus;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class AccountProvisioningServiceImplTest {

    private static final CurrentUser ADMIN =
            new CurrentUser(1L, "kc-admin", "admin.dev", "Admin", null, Set.of(CareNestRole.ADMIN));
    private static final String KC_ID = "kc-new-teacher";

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final IdentityProviderClient identityProviderClient = mock(IdentityProviderClient.class);
    private final AuthProperties authProperties = mock(AuthProperties.class);
    private final AuthGatewayClient authGatewayClient = mock(AuthGatewayClient.class);
    private final AuthStateStore authState = mock(AuthStateStore.class);
    private final AccountProvisioningServiceImpl service = new AccountProvisioningServiceImpl(
            userAccountRepository,
            identityProviderClient,
            new AccountMapper(),
            authProperties,
            authGatewayClient,
            authState);

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

        CreateAccountResponse response =
                service.createAccount(ADMIN, request("parent.c", "parent.c@example.test", CareNestRole.PARENT, null));

        verify(identityProviderClient).sendSetupEmail(KC_ID);
        assertThat(response.setupEmailSent()).isTrue();
    }

    @Test
    void enabledGatewaySendsReactSetupEmail() {
        given(authProperties.enabled()).willReturn(true);
        given(identityProviderClient.canSendSetupEmail()).willReturn(true);
        given(identityProviderClient.createUser(any())).willReturn(KC_ID);
        CreateAccountResponse response =
                service.createAccount(ADMIN, request("parent.c", "parent.c@example.test", CareNestRole.PARENT, null));
        verify(authGatewayClient).sendReactSetupEmail(KC_ID);
        verify(identityProviderClient, never()).sendSetupEmail(anyString());
        assertThat(response.setupEmailSent()).isTrue();
    }

    @Test
    void requiresTemporaryPasswordWhenNoSetupEmailCanBeSent() {
        assertThatThrownBy(() -> service.createAccount(ADMIN, request("parent.c", null, CareNestRole.PARENT, null)))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.BAD_REQUEST);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    void refusesToCreateAdminAccounts() {
        assertThatThrownBy(
                        () -> service.createAccount(ADMIN, request("admin.2", null, CareNestRole.ADMIN, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .hasMessage("This role cannot be created via API");
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    void rejectsExistingUsernameBeforeCallingKeycloak() {
        given(userAccountRepository.existsByUsername("teacher.a")).willReturn(true);

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.a", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CONFLICT);
        verify(identityProviderClient, never()).createUser(any());
    }

    @Test
    void keycloakConflictBecomes409WithoutCompensation() {
        given(identityProviderClient.createUser(any()))
                .willThrow(new IdentityProviderException(IdentityProviderErrorReason.CONFLICT, "exists", null));

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.c", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CONFLICT);
        verify(identityProviderClient, never()).deleteUser(anyString());
    }

    @Test
    void keycloakDownBecomes503() {
        given(identityProviderClient.createUser(any()))
                .willThrow(new IdentityProviderException(IdentityProviderErrorReason.UNAVAILABLE, "down", null));

        assertThatThrownBy(() ->
                        service.createAccount(ADMIN, request("teacher.c", null, CareNestRole.TEACHER, "Temp-Pass-1")))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.SERVICE_UNAVAILABLE);
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
                .extracting("apiCode")
                .isEqualTo(ApiCode.SERVICE_UNAVAILABLE);
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
                .extracting("apiCode")
                .isEqualTo(ApiCode.CONFLICT);
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
                .extracting("apiCode")
                .isEqualTo(ApiCode.CONFLICT);
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
                .extracting("apiCode")
                .isEqualTo(ApiCode.SERVICE_UNAVAILABLE);
        verify(userAccountRepository).saveAndFlush(account);
        assertThat(account.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void unknownAccountReturns404() {
        given(userAccountRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAccountStatus(ADMIN, 99L, UserStatus.INACTIVE))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.NOT_FOUND);
        verifyNoInteractions(identityProviderClient);
    }

    @Test
    void adminCannotChangeOwnAccountStatus() {
        assertThatThrownBy(() -> service.updateAccountStatus(ADMIN, ADMIN.userId(), UserStatus.INACTIVE))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.BAD_REQUEST);
        verifyNoInteractions(userAccountRepository, identityProviderClient);
    }

    private static CreateAccountRequest request(String username, String email, CareNestRole role, String password) {
        return new CreateAccountRequest(username, "Giáo viên Demo", email, role, password);
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
                .extracting("apiCode")
                .isEqualTo(ApiCode.BAD_REQUEST);
        verify(identityProviderClient, never()).deleteUser(anyString());
    }
}
