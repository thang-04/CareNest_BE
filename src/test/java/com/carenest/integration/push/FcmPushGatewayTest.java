package com.carenest.integration.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.utils.enums.DevicePlatform;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FCM HTTP v1 qua server giả: ký JWT đúng, cache access token, phân loại lỗi (research R3). */
class FcmPushGatewayTest {
    private static final PushGateway.PushMessage MESSAGE = new PushGateway.PushMessage(
            "device-token", DevicePlatform.ANDROID, "Tiêu đề", "Nội dung", Map.of("notificationId", "7"));

    @TempDir
    Path dir;

    private HttpServer server;
    private KeyPair keys;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final List<String> sendBodies = new CopyOnWriteArrayList<>();
    private volatile int sendStatus = 200;
    private volatile String sendBody = "{\"name\":\"projects/demo/messages/1\"}";
    private volatile String retryAfter;
    private FcmPushGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", ex -> {
            tokenCalls.incrementAndGet();
            respond(ex, 200, "{\"access_token\":\"at-1\",\"expires_in\":3600}");
        });
        server.createContext("/v1/projects/demo/messages:send", ex -> {
            sendBodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (retryAfter != null) {
                ex.getResponseHeaders().add("Retry-After", retryAfter);
            }
            respond(ex, sendStatus, sendBody);
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded())
                + "\\n-----END PRIVATE KEY-----\\n";
        Path sa = dir.resolve("sa.json");
        Files.writeString(
                sa,
                "{\"project_id\":\"demo\",\"client_email\":\"push@demo.iam\",\"token_uri\":\"" + base
                        + "/token\",\"private_key\":\"" + pem + "\"}");
        gateway = new FcmPushGateway(
                sa,
                Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneOffset.UTC),
                base,
                HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Test
    void sendsMessageAndCachesAccessToken() {
        gateway.send(MESSAGE);
        gateway.send(MESSAGE);

        assertThat(tokenCalls.get()).isEqualTo(1);
        assertThat(sendBodies).hasSize(2);
        assertThat(sendBodies.getFirst())
                .contains("\"token\":\"device-token\"")
                .contains("\"notificationId\":\"7\"")
                .contains("Tiêu đề");
    }

    @Test
    void jwtIsSignedWithServiceAccountKey() throws Exception {
        String jwt = gateway.signedJwt(Instant.parse("2026-11-15T03:00:00Z"));
        String[] parts = jwt.split("\\.");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keys.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        assertThat(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
                .contains("\"iss\":\"push@demo.iam\"")
                .contains("firebase.messaging");
    }

    @Test
    void unregisteredTokenIsInvalid() {
        sendStatus = 404;
        sendBody = "{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}";

        assertThatThrownBy(() -> gateway.send(MESSAGE))
                .isInstanceOfSatisfying(
                        PushException.class, ex -> assertThat(ex.kind()).isEqualTo(PushException.Kind.INVALID_TOKEN));
    }

    @Test
    void missingApnsKeyIsPermanentNotInvalid() {
        sendStatus = 401;
        sendBody = "{\"error\":{\"code\":401,\"details\":[{\"errorCode\":\"THIRD_PARTY_AUTH_ERROR\"}]}}";

        assertThatThrownBy(() -> gateway.send(MESSAGE))
                .isInstanceOfSatisfying(
                        PushException.class, ex -> assertThat(ex.kind()).isEqualTo(PushException.Kind.PERMANENT));
    }

    @Test
    void quotaExceededIsRetryableWithRetryAfter() {
        sendStatus = 429;
        retryAfter = "120";
        sendBody = "{\"error\":{\"code\":429,\"details\":[{\"errorCode\":\"QUOTA_EXCEEDED\"}]}}";

        assertThatThrownBy(() -> gateway.send(MESSAGE)).isInstanceOfSatisfying(PushException.class, ex -> {
            assertThat(ex.kind()).isEqualTo(PushException.Kind.RETRYABLE);
            assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(120));
        });
    }

    @Test
    void serverErrorIsRetryable() {
        sendStatus = 503;
        sendBody = "";

        assertThatThrownBy(() -> gateway.send(MESSAGE))
                .isInstanceOfSatisfying(
                        PushException.class, ex -> assertThat(ex.kind()).isEqualTo(PushException.Kind.RETRYABLE));
    }
}
