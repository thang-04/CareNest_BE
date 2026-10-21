package com.carenest.keycloak;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import java.time.Clock;
import java.util.Map;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.AppAuthManager;

@Provider
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class CareNestAuthResource {
    private final KeycloakSession session;

    CareNestAuthResource(KeycloakSession session) {
        this.session = session;
    }

    @POST
    @Path("password/capability")
    public Map<String, Object> verifiedCapability(AssertionRequest request) {
        AuthOperations operations = authorizedOperations();
        if (request == null) throw new BadRequestException("Request is required");
        String token = operations.verifiedCapability(
                request.assertion(), System.getenv("KEYCLOAK_AUTH_RECOVERY_ASSERTION_SECRET"));
        return Map.of("token", token, "expiresIn", SetupCapabilities.TTL_SECONDS);
    }

    public record AssertionRequest(String assertion) {
        @Override
        public String toString() {
            return "AssertionRequest[redacted]";
        }
    }

    @POST
    @Path("password/setup")
    public Map<String, Boolean> setup(SetupRequest request) {
        AuthOperations operations = authorizedOperations();
        if (request == null) throw new BadRequestException("Request is required");
        operations.setup(request.token(), request.newPassword(), request.confirmPassword());
        return Map.of("success", true);
    }

    @POST
    @Path("recovery/email")
    public Map<String, Boolean> recoveryEmail(OtpEmailRequest request) {
        AuthOperations operations = authorizedOperations();
        if (request == null) throw new BadRequestException("Request is required");
        operations.sendOtp(request.userId(), request.code());
        return Map.of("success", true);
    }

    @POST
    @Path("setup/email")
    public Map<String, Boolean> setupEmail(SetupEmailRequest request) {
        AuthOperations operations = authorizedOperations();
        if (request == null) throw new BadRequestException("Request is required");
        operations.sendSetupEmail(
                request.userId(), System.getenv().getOrDefault("CARENEST_AUTH_WEB_ORIGIN", "http://localhost:5173"));
        return Map.of("success", true);
    }

    private AuthOperations authorizedOperations() {
        RealmModel realm = session.getContext().getRealm();
        var auth = new AppAuthManager.BearerTokenAuthenticator(session)
                .setRealm(realm)
                .setConnection(session.getContext().getConnection())
                .setUriInfo(session.getContext().getUri())
                .setHeaders(session.getContext().getRequestHeaders())
                .authenticate();
        ServiceAccountAuthorizer.require(auth, realm.getClientByClientId("carenest-backend"));
        ClientModel gateway = realm.getClientByClientId(SetupCapabilities.GATEWAY_CLIENT);
        if (gateway == null || !gateway.isEnabled())
            throw new ServiceUnavailableException("Web auth gateway is disabled");
        return new AuthOperations(session, realm, Clock.systemUTC());
    }

    public record SetupRequest(String token, String newPassword, String confirmPassword) {
        @Override
        public String toString() {
            return "SetupRequest[redacted]";
        }
    }

    public record OtpEmailRequest(String userId, String code) {
        @Override
        public String toString() {
            return "OtpEmailRequest[redacted]";
        }
    }

    public record SetupEmailRequest(String userId) {}
}
