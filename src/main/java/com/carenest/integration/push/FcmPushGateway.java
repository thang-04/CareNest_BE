package com.carenest.integration.push;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Đẩy qua Firebase Cloud Messaging HTTP v1 bằng JDK (không thêm thư viện): access token OAuth2 lấy bằng JWT ký RS256 từ
 * file service account, cache tới gần hết hạn. Không ghi token thiết bị, khóa hay nội dung vào log.
 */
public class FcmPushGateway implements PushGateway {
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);
    private static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);
    // Token thiết bị hỏng hoặc App đã gỡ: xóa thiết bị (research R3)
    private static final Set<String> INVALID_TOKEN_CODES =
            Set.of("UNREGISTERED", "SENDER_ID_MISMATCH", "INVALID_ARGUMENT");

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http;
    private final Clock clock;
    private final String fcmBaseUrl;
    private final String projectId;
    private final String clientEmail;
    private final String tokenUri;
    private final PrivateKey signingKey;

    private String bearer;
    private Instant bearerExpiresAt = Instant.MIN;

    public FcmPushGateway(Path credentialsFile, Clock clock) {
        this(
                credentialsFile,
                clock,
                "https://fcm.googleapis.com",
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    FcmPushGateway(Path credentialsFile, Clock clock, String fcmBaseUrl, HttpClient http) {
        this.clock = clock;
        this.fcmBaseUrl = fcmBaseUrl;
        this.http = http;
        try {
            JsonNode sa = json.readTree(Files.readString(credentialsFile));
            this.projectId = sa.path("project_id").asString();
            this.clientEmail = sa.path("client_email").asString();
            this.tokenUri = sa.path("token_uri").asString("https://oauth2.googleapis.com/token");
            this.signingKey = parsePrivateKey(sa.path("private_key").asString());
        } catch (IOException | GeneralSecurityException | RuntimeException ex) {
            throw new IllegalStateException(
                    "Không đọc được file khóa Firebase (carenest.push.fcm.credentials-file)", ex);
        }
    }

    @Override
    public void send(PushMessage message) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", message.title());
        notification.put("body", message.body());
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("token", message.token());
        msg.put("notification", notification);
        msg.put("data", message.data());
        msg.put("android", Map.of("priority", "HIGH"));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(fcmBaseUrl + "/v1/projects/" + projectId + "/messages:send"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + bearer())
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("message", msg))))
                .build();
        HttpResponse<String> response = call(request);
        if (response.statusCode() / 100 != 2) {
            throw classify(response);
        }
    }

    private PushException classify(HttpResponse<String> response) {
        int status = response.statusCode();
        String errorCode = errorCode(response.body());
        Duration retryAfter = response.headers()
                .firstValue("Retry-After")
                .map(FcmPushGateway::parseSeconds)
                .orElse(null);
        String info = "FCM " + status + " " + errorCode;
        if (INVALID_TOKEN_CODES.contains(errorCode)) {
            return new PushException(PushException.Kind.INVALID_TOKEN, info);
        }
        if ("THIRD_PARTY_AUTH_ERROR".equals(errorCode)) {
            // iOS chưa có khóa APNs: giữ thiết bị để dùng khi có khóa (FR-004)
            return new PushException(PushException.Kind.PERMANENT, info);
        }
        if (status == 401) {
            bearerExpiresAt = Instant.MIN;
            return new PushException(PushException.Kind.RETRYABLE, info, retryAfter);
        }
        if (status == 429 || status >= 500) {
            return new PushException(PushException.Kind.RETRYABLE, info, retryAfter);
        }
        return new PushException(PushException.Kind.PERMANENT, info);
    }

    private String errorCode(String body) {
        try {
            JsonNode error =
                    json.readTree(body == null || body.isBlank() ? "{}" : body).path("error");
            for (JsonNode d : error.path("details")) {
                if (d.has("errorCode")) {
                    return d.path("errorCode").asString();
                }
            }
            return error.path("status").asString("");
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private synchronized String bearer() {
        Instant now = clock.instant();
        if (bearer != null && now.isBefore(bearerExpiresAt.minus(TOKEN_MARGIN))) {
            return bearer;
        }
        String form = "grant_type="
                + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + signedJwt(now);
        HttpResponse<String> response = call(HttpRequest.newBuilder(URI.create(tokenUri))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());
        if (response.statusCode() / 100 != 2) {
            throw new PushException(
                    response.statusCode() >= 500 ? PushException.Kind.RETRYABLE : PushException.Kind.PERMANENT,
                    "FCM token " + response.statusCode());
        }
        JsonNode body = json.readTree(response.body());
        bearer = body.path("access_token").asString();
        bearerExpiresAt = now.plusSeconds(body.path("expires_in").asLong(TOKEN_LIFETIME.toSeconds()));
        return bearer;
    }

    String signedJwt(Instant now) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", clientEmail);
        claims.put("scope", SCOPE);
        claims.put("aud", tokenUri);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(TOKEN_LIFETIME).getEpochSecond());
        String unsigned = header + "." + b64.encodeToString(json.writeValueAsBytes(claims));
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(signingKey);
            signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + b64.encodeToString(signer.sign());
        } catch (GeneralSecurityException ex) {
            throw new PushException(
                    PushException.Kind.PERMANENT, "FCM sign " + ex.getClass().getSimpleName());
        }
    }

    private HttpResponse<String> call(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new PushException(
                    PushException.Kind.RETRYABLE, "FCM io " + ex.getClass().getSimpleName());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PushException(PushException.Kind.RETRYABLE, "FCM interrupted");
        }
    }

    private static PrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "") // carenest:allow-secret
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    private static Duration parseSeconds(String value) {
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
