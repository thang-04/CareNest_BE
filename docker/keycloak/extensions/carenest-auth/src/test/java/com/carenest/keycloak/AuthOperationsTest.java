package com.carenest.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.credential.CredentialModel;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.*;
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.policy.PolicyError;

class AuthOperationsTest {
    private final KeycloakSession session = mock(KeycloakSession.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final UserModel user = mock(UserModel.class);
    private final SubjectCredentialManager credentials = mock(SubjectCredentialManager.class);
    private final PasswordPolicyManagerProvider policy = mock(PasswordPolicyManagerProvider.class);
    private final SingleUseObjectProvider cache = mock(SingleUseObjectProvider.class);
    private final Map<String, Map<String, String>> objects = new HashMap<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC);
    private final CredentialModel credential = new CredentialModel();
    private AuthOperations operations;
    private SetupCapabilities capabilities;

    @Test
    void capabilityLockIsReleasedOnlyAfterKeycloakCommitOrRollback() {
        var transaction = mock(KeycloakTransactionManager.class);
        when(session.getTransactionManager()).thenReturn(transaction);
        when(transaction.isActive()).thenReturn(true);
        var completions = new java.util.ArrayList<KeycloakTransaction>();
        doAnswer(call -> {
                    KeycloakTransaction completion = call.getArgument(0);
                    completion.begin();
                    completions.add(completion);
                    return null;
                })
                .when(transaction)
                .enlistAfterCompletion(any());
        String token = capabilities.issue(user);
        assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> capabilities.issue(user));
        completions.getFirst().commit();
        assertDoesNotThrow(() -> operations.setup(token, "New-Password-123", "New-Password-123"));
        assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> capabilities.issue(user));
        completions.getLast().rollback();
        assertDoesNotThrow(() -> capabilities.issue(user));
    }

    @Test
    void cannotIssueAnotherCapabilityDuringCredentialMutation() {
        String token = capabilities.issue(user);
        when(credentials.updateCredential(any())).thenAnswer(ignored -> {
            assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> capabilities.issue(user));
            return true;
        });
        assertDoesNotThrow(() -> operations.setup(token, "New-Password-123", "New-Password-123"));
    }

    @Test
    void emailProviderCanEnrichTemplateAttributes() throws Exception {
        when(user.getEmail()).thenReturn("user@example.test");
        var email = mock(EmailTemplateProvider.class);
        when(session.getProvider(EmailTemplateProvider.class)).thenReturn(email);
        when(email.setRealm(realm)).thenReturn(email);
        when(email.setUser(user)).thenReturn(email);
        doAnswer(call -> {
                    Map<String, Object> attributes = call.getArgument(2);
                    attributes.put("locale", java.util.Locale.ENGLISH);
                    return null;
                })
                .when(email)
                .send(anyString(), anyString(), anyMap());
        assertDoesNotThrow(() -> operations.sendOtp("user-unit", "012345"));
    }

    @BeforeEach
    void prepare() {
        when(session.singleUseObjects()).thenReturn(cache);
        when(realm.getId()).thenReturn("realm-unit");
        when(realm.getName()).thenReturn("carenest");
        when(user.getId()).thenReturn("user-unit");
        when(user.isEnabled()).thenReturn(true);
        when(user.getRequiredActionsStream()).thenAnswer(ignored -> Stream.of("UPDATE_PASSWORD"));
        when(user.credentialManager()).thenReturn(credentials);
        credential.setId("credential-unit");
        credential.setSecretData("old-binding");
        when(credentials.getStoredCredentialsByTypeStream("password")).thenAnswer(ignored -> Stream.of(credential));
        when(credentials.updateCredential(any())).thenReturn(true);
        var users = mock(org.keycloak.storage.UserStorageManager.class);
        when(session.users()).thenReturn(users);
        when(users.getUserById(realm, "user-unit")).thenReturn(user);
        when(session.getProvider(PasswordPolicyManagerProvider.class)).thenReturn(policy);
        when(session.sessions()).thenReturn(mock(UserSessionProvider.class));
        when(session.sessions().getOfflineUserSessionsStream(realm, user)).thenAnswer(ignored -> Stream.empty());
        doAnswer(call -> {
                    objects.put(call.getArgument(0), call.getArgument(2));
                    return null;
                })
                .when(cache)
                .put(anyString(), anyLong(), anyMap());
        when(cache.get(anyString())).thenAnswer(call -> objects.get(call.getArgument(0)));
        when(cache.remove(anyString())).thenAnswer(call -> objects.remove(call.getArgument(0)));
        when(cache.putIfAbsent(anyString(), anyLong()))
                .thenAnswer(call -> objects.putIfAbsent(call.getArgument(0), Map.of()) == null);
        capabilities = new SetupCapabilities(session, realm, clock);
        operations = new AuthOperations(session, realm, clock);
    }

    @Test
    void setupChangesPasswordRevokesSessionsAndRejectsReplay() {
        String token = capabilities.issue(user);
        assertDoesNotThrow(() -> operations.setup(token, "New-Password-123", "New-Password-123"));
        verify(credentials).updateCredential(any());
        verify(user).removeRequiredAction("UPDATE_PASSWORD");
        verify(session.sessions()).removeUserSessions(realm, user);
        assertThrows(
                BadRequestException.class, () -> operations.setup(token, "Other-Password-123", "Other-Password-123"));
        verify(credentials, times(1)).updateCredential(any());
    }

    @Test
    void verifiedRecoveryChangesRegularPasswordWithoutRequiredAction() throws Exception {
        verifiedRegularPasswordChange("RECOVERY_VERIFIED");
    }

    @Test
    void verifiedCurrentPasswordChangesRegularPasswordWithoutRequiredAction() throws Exception {
        verifiedRegularPasswordChange("CURRENT_PASSWORD_VERIFIED");
    }

    private void verifiedRegularPasswordChange(String purpose) throws Exception {
        when(user.getRequiredActionsStream()).thenAnswer(ignored -> Stream.empty());
        String proof = AssertionFixture.sign(AssertionFixture.claims(clock, "user-unit", purpose));
        String token = operations.verifiedCapability(proof, AssertionFixture.ENCODED_SECRET);
        verify(credentials, never()).updateCredential(any());
        assertThrows(
                BadRequestException.class, () -> operations.verifiedCapability(proof, AssertionFixture.ENCODED_SECRET));
        operations.setup(token, "New-Password-123", "New-Password-123");
        verify(credentials).updateCredential(any());
        verify(session.sessions()).removeUserSessions(realm, user);
        assertThrows(
                BadRequestException.class, () -> operations.setup(token, "Other-Password-123", "Other-Password-123"));
    }

    @Test
    void verifiedCapabilityRemainsBoundToCredentialAndAccountStatus() throws Exception {
        String proof = AssertionFixture.sign(AssertionFixture.claims(clock, "user-unit", "RECOVERY_VERIFIED"));
        String token = operations.verifiedCapability(proof, AssertionFixture.ENCODED_SECRET);
        credential.setSecretData("changed-outside-gateway");
        assertThrows(BadRequestException.class, () -> operations.setup(token, "New-Password-123", "New-Password-123"));
        when(user.isEnabled()).thenReturn(false);
        var nextClaims = AssertionFixture.claims(clock, "user-unit", "RECOVERY_VERIFIED");
        nextClaims.put("nonce", "o".repeat(43));
        String nextProof = AssertionFixture.sign(nextClaims);
        assertThrows(
                BadRequestException.class,
                () -> operations.verifiedCapability(nextProof, AssertionFixture.ENCODED_SECRET));
        verify(credentials, never()).updateCredential(any());
    }

    @Test
    void unsignedUserIdCannotObtainPasswordCapability() {
        assertThrows(
                BadRequestException.class,
                () -> operations.verifiedCapability("user-unit", AssertionFixture.ENCODED_SECRET));
        verify(credentials, never()).updateCredential(any());
    }

    @Test
    void policyFailureDoesNotConsumeSetupCapability() {
        String token = capabilities.issue(user);
        when(policy.validate(realm, user, "short")).thenReturn(new PolicyError("invalidPasswordMinLengthMessage", 8));
        assertThrows(BadRequestException.class, () -> operations.setup(token, "short", "short"));
        assertDoesNotThrow(() -> operations.setup(token, "New-Password-123", "New-Password-123"));
        verify(credentials, times(1)).updateCredential(any());
    }

    @Test
    void mismatchedConfirmationDoesNotMutateCredential() {
        String token = capabilities.issue(user);
        assertThrows(BadRequestException.class, () -> operations.setup(token, "New-Password-123", "different"));
        verify(credentials, never()).updateCredential(any());
    }

    @Test
    void disabledAccountCannotRedeemPreviouslyIssuedCapability() {
        String token = capabilities.issue(user);
        when(user.isEnabled()).thenReturn(false);
        assertThrows(BadRequestException.class, () -> operations.setup(token, "New-Password-123", "New-Password-123"));
        verify(credentials, never()).updateCredential(any());
    }

    @Test
    void previousCapabilityIsInvalidatedWhenANewOneIsIssued() {
        String oldToken = capabilities.issue(user);
        String fresh = capabilities.issue(user);
        assertThrows(
                BadRequestException.class, () -> operations.setup(oldToken, "New-Password-123", "New-Password-123"));
        assertDoesNotThrow(() -> operations.setup(fresh, "New-Password-123", "New-Password-123"));
    }

    @Test
    void nativeCredentialChangeInvalidatesCapabilityEvenIfUpdatePasswordIsStillRequired() {
        String token = capabilities.issue(user);
        credential.setSecretData("changed-by-native-keycloak");
        assertThrows(BadRequestException.class, () -> operations.setup(token, "New-Password-123", "New-Password-123"));
        verify(credentials, never()).updateCredential(any());
    }

    @Test
    void explicitlyChecksExpirationEvenIfTheCacheRetainsTheEntry() {
        String token = capabilities.issue(user);
        var later = new AuthOperations(session, realm, Clock.offset(clock, java.time.Duration.ofSeconds(600)));
        assertThrows(BadRequestException.class, () -> later.setup(token, "New-Password-123", "New-Password-123"));
    }

    @Test
    void setupDoesNotBypassUnrelatedRequiredActions() {
        String token = capabilities.issue(user);
        when(user.getRequiredActionsStream()).thenAnswer(ignored -> Stream.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
        assertThrows(BadRequestException.class, () -> operations.setup(token, "New-Password-123", "New-Password-123"));
        verify(credentials, never()).updateCredential(any());
        verify(user, never()).removeRequiredAction("VERIFY_EMAIL");
    }

    @Test
    void otpEmailUsesKeycloakTemplateAndRejectsMalformedCode() throws Exception {
        when(user.getEmail()).thenReturn("user@example.test");
        var email = mock(EmailTemplateProvider.class);
        when(session.getProvider(EmailTemplateProvider.class)).thenReturn(email);
        when(email.setRealm(realm)).thenReturn(email);
        when(email.setUser(user)).thenReturn(email);
        assertDoesNotThrow(() -> operations.sendOtp("user-unit", "012345"));
        verify(email)
                .send(
                        eq("carenestRecoverySubject"),
                        eq("carenest-recovery-code.ftl"),
                        eq(Map.of("code", "012345", "minutes", 5)));
        assertThrows(BadRequestException.class, () -> operations.sendOtp("user-unit", "<script>"));
    }
}
