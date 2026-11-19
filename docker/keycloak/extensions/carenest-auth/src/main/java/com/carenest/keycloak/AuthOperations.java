package com.carenest.keycloak;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;
import java.net.URI;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.policy.PasswordPolicyManagerProvider;

final class AuthOperations {
    static final String DEFAULT_SCHOOL_NAME = "Trường Mầm Non Thượng Hồng";
    private static final Map<String, String> STAFF_ROLE_LABELS = new LinkedHashMap<>();

    static {
        STAFF_ROLE_LABELS.put("PRINCIPAL", "Hiệu trưởng");
        STAFF_ROLE_LABELS.put("VICE_PRINCIPAL", "Phó hiệu trưởng");
        STAFF_ROLE_LABELS.put("TEACHER", "Giáo viên");
        STAFF_ROLE_LABELS.put("KITCHEN_STAFF", "Nhân viên bếp");
        STAFF_ROLE_LABELS.put("ADMIN", "Quản trị hệ thống");
    }

    private final KeycloakSession session;
    private final RealmModel realm;
    private final SetupCapabilities capabilities;
    private final Clock clock;
    private final Function<String, String> env;

    AuthOperations(KeycloakSession session, RealmModel realm, Clock clock) {
        this(session, realm, clock, System::getenv);
    }

    AuthOperations(KeycloakSession session, RealmModel realm, Clock clock, Function<String, String> env) {
        this.session = session;
        this.realm = realm;
        this.capabilities = new SetupCapabilities(session, realm, clock);
        this.clock = clock;
        this.env = env;
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

    void sendOtp(String userId, String code, String webOrigin) {
        if (code == null || !code.matches("[0-9]{6}")) throw new BadRequestException("Invalid recovery code");
        UserModel user = user(userId);
        // OTP là đường khôi phục: origin lỗi chỉ làm mất logo, không chặn gửi mã
        Map<String, Object> attributes = commonAttributes(user, safeOrigin(webOrigin));
        attributes.put("code", code);
        attributes.put("minutes", 5);
        sendEmail(user, "carenestRecoverySubject", "carenest-recovery-code.ftl", attributes);
    }

    void sendSetupEmail(String userId, String webOrigin) {
        String origin = safeOrigin(webOrigin);
        if (origin == null) throw new ServiceUnavailableException("Web origin is not configured");
        UserModel user = user(userId);
        Map<String, Object> attributes = commonAttributes(user, origin);
        attributes.put("link", origin + "/forgot-password");
        attributes.put("roleLabel", staffRoleLabel(user));
        sendEmail(user, "carenestSetupSubject", "carenest-setup.ftl", attributes);
    }

    private Map<String, Object> commonAttributes(UserModel user, String origin) {
        Map<String, Object> attributes = new HashMap<>();
        // BE lưu cả họ tên vào firstName (tên tiếng Việt không tách họ/tên tin cậy)
        String fullName = user.getFirstName();
        attributes.put("fullName", fullName == null ? "" : fullName.trim());
        attributes.put("username", user.getUsername());
        String school = env.apply("CARENEST_SCHOOL_NAME");
        attributes.put("schoolName", school == null || school.isBlank() ? DEFAULT_SCHOOL_NAME : school.trim());
        if (origin != null) attributes.put("logoUrl", origin + "/email/logo-mark.png");
        return attributes;
    }

    // Nhân sự kiêm phụ huynh ⇒ lấy role nhân sự cao nhất; không có role nhân sự ⇒ mail không ghi vai trò
    private String staffRoleLabel(UserModel user) {
        for (Map.Entry<String, String> entry : STAFF_ROLE_LABELS.entrySet()) {
            RoleModel role = realm.getRole(entry.getKey());
            if (role != null && user.hasRole(role)) return entry.getValue();
        }
        return null;
    }

    private static String safeOrigin(String webOrigin) {
        URI origin;
        try {
            origin = URI.create(webOrigin);
        } catch (RuntimeException ex) {
            return null;
        }
        if (origin.getHost() == null
                || origin.getUserInfo() != null
                || origin.getQuery() != null
                || origin.getFragment() != null
                || (origin.getPath() != null && !origin.getPath().isEmpty() && !"/".equals(origin.getPath()))
                || !("https".equals(origin.getScheme())
                        || ("http".equals(origin.getScheme()) && "localhost".equals(origin.getHost())))) {
            return null;
        }
        return webOrigin.replaceAll("/+$", "");
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
                    .send(subject, template, attributes);
        } catch (EmailException ex) {
            throw new ServiceUnavailableException("Email delivery is unavailable");
        }
    }
}
