package com.carenest.keycloak;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.ArgumentCaptor;

class SetupRequiredAuthenticatorTest {
    private final AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
    private final KeycloakSession session = mock(KeycloakSession.class);
    private final UserModel user = mock(UserModel.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final ClientModel client = mock(ClientModel.class);
    private final AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
    private final SingleUseObjectProvider cache = mock(SingleUseObjectProvider.class);

    @BeforeEach
    void prepare() {
        when(context.getSession()).thenReturn(session);
        when(context.getUser()).thenReturn(user);
        when(context.getRealm()).thenReturn(realm);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(authSession.getClient()).thenReturn(client);
        when(client.getClientId()).thenReturn("carenest-auth-gateway");
        when(realm.getId()).thenReturn("realm-test");
        when(user.getId()).thenReturn("user-test");
        when(user.isEnabled()).thenReturn(true);
        when(session.singleUseObjects()).thenReturn(cache);
        when(cache.putIfAbsent(any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        when(authSession.getRequiredActions()).thenReturn(java.util.Set.of());
        var credentials = mock(SubjectCredentialManager.class);
        var password = new CredentialModel();
        password.setId("credential-test");
        password.setSecretData("test-credential-binding");
        when(user.credentialManager()).thenReturn(credentials);
        when(credentials.getStoredCredentialsByTypeStream("password")).thenAnswer(ignored -> Stream.of(password));
    }

    private Authenticator checkpoint() {
        return assertDoesNotThrow(
                () -> (Authenticator) Class.forName("com.carenest.keycloak.SetupRequiredAuthenticator")
                        .getConstructor()
                        .newInstance(),
                "The verified-password checkpoint must be installed");
    }

    @Test
    void completeAccountContinuesNativeTokenIssuance() {
        when(user.getRequiredActionsStream()).thenReturn(Stream.empty());
        checkpoint().authenticate(context);
        verify(context).success();
        verify(context, never()).challenge(any());
        verify(session, never()).singleUseObjects();
    }

    @Test
    void temporaryPasswordProducesOnlyASetupCapability() {
        when(user.getRequiredActionsStream()).thenReturn(Stream.of("UPDATE_PASSWORD"));
        checkpoint().authenticate(context);
        var response = ArgumentCaptor.forClass(Response.class);
        verify(context).challenge(response.capture());
        verify(context, never()).success();
        assertEquals(400, response.getValue().getStatus());
        Map<?, ?> body = (Map<?, ?>) response.getValue().getEntity();
        assertEquals("carenest_password_change_required", body.get("error"));
        assertEquals("user-test", body.get("user_id"));
        assertEquals(600L, body.get("expires_in"));
        assertEquals(43, body.get("setup_token").toString().length());
        assertFalse(body.containsKey("access_token"));
        assertFalse(body.containsKey("refresh_token"));
    }

    @Test
    void unrelatedRequiredActionsNeverProduceASetupCapability() {
        when(user.getRequiredActionsStream()).thenReturn(Stream.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
        checkpoint().authenticate(context);
        verify(context).success();
        verify(context, never()).challenge(any());
        verify(session, never()).singleUseObjects();
    }

    @Test
    void checkpointCannotBeReusedByAnUnrelatedClient() {
        when(client.getClientId()).thenReturn("carenest-mobile");
        checkpoint().authenticate(context);
        verify(context, never()).success();
        verify(session, never()).singleUseObjects();
    }

    @Test
    void disabledUserCannotObtainASetupCapability() {
        when(user.isEnabled()).thenReturn(false);
        checkpoint().authenticate(context);
        verify(context, never()).success();
        verify(session, never()).singleUseObjects();
    }
}
