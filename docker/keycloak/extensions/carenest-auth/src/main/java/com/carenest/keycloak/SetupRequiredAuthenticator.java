package com.carenest.keycloak;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public final class SetupRequiredAuthenticator implements Authenticator {
    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (!SetupCapabilities.GATEWAY_CLIENT.equals(
                        context.getAuthenticationSession().getClient().getClientId())
                || user == null
                || !user.isEnabled()) {
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }
        Set<String> actions = new HashSet<>(context.getAuthenticationSession().getRequiredActions());
        user.getRequiredActionsStream().forEach(actions::add);
        if (!actions.equals(Set.of("UPDATE_PASSWORD"))) {
            // Để Keycloak xử lý các required action khác, không bỏ qua chúng bằng setup token.
            context.success();
            return;
        }
        try {
            var capabilities = new SetupCapabilities(context.getSession(), context.getRealm(), Clock.systemUTC());
            String token = capabilities.issue(user);
            context.challenge(Response.status(400)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(Map.of(
                            "error",
                            "carenest_password_change_required",
                            "user_id",
                            user.getId(),
                            "setup_token",
                            token,
                            "expires_in",
                            SetupCapabilities.TTL_SECONDS))
                    .build());
        } catch (BadRequestException ex) {
            context.failure(AuthenticationFlowError.CREDENTIAL_SETUP_REQUIRED);
        } catch (ClientErrorException ex) {
            context.challenge(Response.status(409)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(Map.of("error", "temporarily_unavailable"))
                    .build());
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

    @Override
    public void action(AuthenticationFlowContext context) {
        context.failure(AuthenticationFlowError.INTERNAL_ERROR);
    }

    @Override
    public void close() {}
}
