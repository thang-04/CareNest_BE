package com.carenest.keycloak;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import org.keycloak.models.ClientModel;
import org.keycloak.services.managers.AuthenticationManager.AuthResult;

final class ServiceAccountAuthorizer {
    static void require(AuthResult auth, ClientModel managementClient) {
        if (auth == null || auth.getToken() == null || auth.getUser() == null)
            throw new NotAuthorizedException("Bearer");
        if (managementClient == null
                || !managementClient.isEnabled()
                || !auth.getUser().isEnabled()
                || !"carenest-backend".equals(managementClient.getClientId())
                || !managementClient.getId().equals(auth.getUser().getServiceAccountClientLink())
                || !"carenest-backend".equals(auth.getToken().getIssuedFor())
                || !auth.getUser().getId().equals(auth.getToken().getSubject())
                || auth.getToken().getResourceAccess("realm-management") == null
                || !auth.getToken().getResourceAccess("realm-management").isUserInRole("manage-users")) {
            throw new ForbiddenException("Backend service account required");
        }
    }
}
