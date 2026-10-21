package com.carenest.keycloak;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;
import java.net.URI;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.policy.PasswordPolicyManagerProvider;

final class AuthOperations {
    private final KeycloakSession session;
    private final RealmModel realm;
    private final SetupCapabilities capabilities;
    private final Clock clock;

    AuthOperations(KeycloakSession session, RealmModel realm, Clock clock) {
        this.session = session;
        this.realm = realm;
        this.capabilities = new SetupCapabilities(session, realm, clock);
        this.clock = clock;
    }

    String verifiedCapability(String assertion, String assertionSecret) {
        String userId = new VerifiedPasswordAssertions(assertionSecret, clock, session.singleUseObjects())
                .verify(assertion, realm);
        return capabilities.issueVerified(user(userId));
    }

    void setup(String token, String password, String confirmation) {
        var notes = capabilities.require(token);
        try (var ignored = capabilities.lock(notes.get("userId"))) {
            setupLocked(token, notes, password, confirmation);
        }
    }

    private void setupLocked(String token, Map<String, String> notes, String password, String confirmation) {
        UserModel user = user(notes.get("userId"));
        capabilities.validateUser(notes, user);
        if (password == null || password.isEmpty() || password.length() > 128 || !password.equals(confirmation)) {
            throw ProviderErrors.bad("password_policy_rejected", "Invalid password or confirmation");
        }
        if (session.getProvider(PasswordPolicyManagerProvider.class).validate(realm, user, password) != null) {
            throw ProviderErrors.bad("password_policy_rejected", "Password rejected by identity provider policy");
        }
        capabilities.consume(token, notes);
        try {
            if (!user.credentialManager().updateCredential(UserCredentialModel.password(password, false))) {
                throw ProviderErrors.bad(
                        "capability_spent", "Password update could not be completed; restart recovery");
            }
        } catch (ModelException ex) {
            throw ProviderErrors.bad("capability_spent", "Password update could not be completed; restart recovery");
        }
        user.removeRequiredAction("UPDATE_PASSWORD");
        capabilities.invalidate(user);
        session.sessions().removeUserSessions(realm, user);
        session.sessions()
                .getOfflineUserSessionsStream(realm, user)
                .forEach(offline -> session.sessions().removeOfflineUserSession(realm, offline));
    }

    void sendOtp(String userId, String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw new BadRequestException("Invalid recovery code");
        sendEmail(
                user(userId),
                "carenestRecoverySubject",
                "carenest-recovery-code.ftl",
                Map.of("code", code, "minutes", 5));
    }

    void sendSetupEmail(String userId, String webOrigin) {
        URI origin;
        try {
            origin = URI.create(webOrigin);
        } catch (RuntimeException ex) {
            throw new ServiceUnavailableException("Web origin is not configured");
        }
        if (origin.getHost() == null
                || origin.getUserInfo() != null
                || origin.getQuery() != null
                || origin.getFragment() != null
                || (origin.getPath() != null && !origin.getPath().isEmpty() && !"/".equals(origin.getPath()))
                || !("https".equals(origin.getScheme())
                        || ("http".equals(origin.getScheme()) && "localhost".equals(origin.getHost())))) {
            throw new ServiceUnavailableException("Web origin is not configured");
        }
        sendEmail(
                user(userId),
                "carenestSetupSubject",
                "carenest-setup.ftl",
                Map.of("link", webOrigin.replaceAll("/+$", "") + "/forgot-password"));
    }

    private UserModel user(String id) {
        if (id == null || id.isBlank() || id.length() > 255) throw new BadRequestException("Invalid account");
        UserModel user = session.users().getUserById(realm, id);
        if (user == null || !user.isEnabled()) throw new BadRequestException("Invalid account");
        return user;
    }

    private void sendEmail(UserModel user, String subject, String template, Map<String, Object> attributes) {
        if (user.getEmail() == null || user.getEmail().isBlank())
            throw new BadRequestException("Email is not configured");
        try {
            session.getProvider(EmailTemplateProvider.class)
                    .setRealm(realm)
                    .setUser(user)
                    .send(subject, template, new HashMap<>(attributes));
        } catch (EmailException ex) {
            throw new ServiceUnavailableException("Email delivery is unavailable");
        }
    }
}
