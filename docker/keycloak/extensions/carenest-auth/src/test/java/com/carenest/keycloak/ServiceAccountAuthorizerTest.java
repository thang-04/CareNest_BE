package com.carenest.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ClientModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.managers.AuthenticationManager.AuthResult;

class ServiceAccountAuthorizerTest {
    private final ClientModel client = mock(ClientModel.class);
    private final UserModel user = mock(UserModel.class);
    private final AccessToken token = new AccessToken();
    private AuthResult auth;

    @BeforeEach
    void prepare() {
        when(client.getId()).thenReturn("backend-client-id");
        when(client.getClientId()).thenReturn("carenest-backend");
        when(client.isEnabled()).thenReturn(true);
        when(user.getId()).thenReturn("backend-service-user");
        when(user.isEnabled()).thenReturn(true);
        when(user.getServiceAccountClientLink()).thenReturn("backend-client-id");
        token.issuedFor("carenest-backend").subject("backend-service-user");
        token.addAccess("realm-management").addRole("manage-users");
        auth = new AuthResult(user, null, token, client);
    }

    @Test
    void acceptsOnlyTheExpectedPrivilegedServiceAccount() {
        assertDoesNotThrow(() -> ServiceAccountAuthorizer.require(auth, client));
    }

    @Test
    void anonymousCallerIsUnauthorized() {
        assertThrows(NotAuthorizedException.class, () -> ServiceAccountAuthorizer.require(null, client));
    }

    @Test
    void normalUserCannotUseServiceTokenPrivileges() {
        when(user.getServiceAccountClientLink()).thenReturn(null);
        assertThrows(ForbiddenException.class, () -> ServiceAccountAuthorizer.require(auth, client));
    }

    @Test
    void wrongClientIsRejected() {
        token.issuedFor("carenest-web");
        assertThrows(ForbiddenException.class, () -> ServiceAccountAuthorizer.require(auth, client));
    }

    @Test
    void missingManageUsersRoleIsRejected() {
        token.getResourceAccess("realm-management").getRoles().clear();
        assertThrows(ForbiddenException.class, () -> ServiceAccountAuthorizer.require(auth, client));
    }

    @Test
    void subjectMustMatchTheAuthenticatedServiceUser() {
        token.subject("some-other-user");
        assertThrows(ForbiddenException.class, () -> ServiceAccountAuthorizer.require(auth, client));
    }

    @Test
    void disabledServiceClientIsRejected() {
        when(client.isEnabled()).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> ServiceAccountAuthorizer.require(auth, client));
    }
}
