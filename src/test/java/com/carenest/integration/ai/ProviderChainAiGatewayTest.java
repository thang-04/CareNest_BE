package com.carenest.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.carenest.utils.enums.AiDataClass;
import com.carenest.utils.enums.AiErrorReason;
import com.carenest.utils.enums.AiTask;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProviderChainAiGatewayTest {

    private static final Instant NOW = Instant.parse("2026-11-10T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String GEMINI_URL = "http://gemini.test/chat/completions";
    private static final String GROQ_URL = "http://groq.test/chat/completions";
    private static final String OK_BODY = """
            {"choices":[{"message":{"content":"{\\"days\\":[]}"}}],"usage":{"prompt_tokens":120,"completion_tokens":30}}
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private final List<Duration> readTimeouts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    @DisplayName("AI-06: gọi provider đầu chuỗi, trả nội dung thô kèm metadata")
    void returnsFirstProviderContentWithMetadata() {
        server.expect(once(), requestTo(GEMINI_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer key-gemini"))
                .andExpect(jsonPath("$.model").value("model-gemini"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("SYS"))
                .andExpect(jsonPath("$.messages[1].content").value("USER"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        AiRawResult result = gateway(publicChain()).complete(request(NOW.plusSeconds(30)));

        assertThat(result.content()).isEqualTo("{\"days\":[]}");
        assertThat(result.providerId()).isEqualTo("gemini");
        assertThat(result.model()).isEqualTo("model-gemini");
        assertThat(result.promptVersion()).isEqualTo("menu/weekly_v1");
        assertThat(result.inputTokens()).isEqualTo(120);
        assertThat(result.outputTokens()).isEqualTo(30);
        server.verify();
    }

    @Test
    @DisplayName("AI-09: provider đầu bị 429 ⇒ chuyển provider kế tiếp")
    void fallsBackToNextProviderWhenRateLimited() {
        server.expect(once(), requestTo(GEMINI_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(GROQ_URL))
                .andExpect(header("Authorization", "Bearer key-groq"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        AiRawResult result = gateway(publicChain()).complete(request(NOW.plusSeconds(30)));

        assertThat(result.providerId()).isEqualTo("groq");
        server.verify();
    }

    @Test
    @DisplayName("AI-03: mọi provider lỗi ⇒ ném lỗi của provider cuối để domain dùng phương án không AI")
    void throwsLastErrorWhenChainExhausted() {
        server.expect(once(), requestTo(GEMINI_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(GROQ_URL)).andRespond(withException(new SocketTimeoutException("slow")));

        assertThatThrownBy(() -> gateway(publicChain()).complete(request(NOW.plusSeconds(30))))
                .isInstanceOf(AiClientException.class)
                .extracting(ex -> ((AiClientException) ex).getReason())
                .isEqualTo(AiErrorReason.TIMEOUT);
        server.verify();
    }

    @Test
    @DisplayName("AI-01: response không có nội dung ⇒ OUTPUT_INVALID, thử provider kế tiếp")
    void emptyContentMovesToNextProvider() {
        server.expect(once(), requestTo(GEMINI_URL))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(GROQ_URL))
                .andRespond(
                        withSuccess("{\"choices\":[{\"message\":{\"content\":\"\"}}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway(publicChain()).complete(request(NOW.plusSeconds(30))))
                .extracting(ex -> ((AiClientException) ex).getReason())
                .isEqualTo(AiErrorReason.OUTPUT_INVALID);
        server.verify();
    }

    @Test
    @DisplayName("AI-03: AI tắt ⇒ không có provider dùng được, không gọi mạng")
    void disabledGatewayDoesNotCallProviders() {
        server.expect(never(), requestTo(GEMINI_URL));
        AiProperties properties = new AiProperties(false, providers(), Map.of(AiTask.MENU_WEEKLY, publicTask()));
        ProviderChainAiGateway gateway = gateway(properties);

        assertThat(gateway.isAvailable(AiTask.MENU_WEEKLY)).isFalse();
        assertThatThrownBy(() -> gateway.complete(request(NOW.plusSeconds(30))))
                .extracting(ex -> ((AiClientException) ex).getReason())
                .isEqualTo(AiErrorReason.DISABLED);
        server.verify();
    }

    @Test
    @DisplayName("AI-09: provider thiếu key bị bỏ khỏi chuỗi")
    void skipsProviderWithoutKey() {
        AiProperties properties = new AiProperties(
                true,
                List.of(
                        new AiProperties.Provider(
                                "gemini", "http://gemini.test", "model-gemini", "", true, null, null, null),
                        provider("groq", false)),
                Map.of(AiTask.MENU_WEEKLY, publicTask()));
        server.expect(once(), requestTo(GROQ_URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        AiRawResult result = gateway(properties).complete(request(NOW.plusSeconds(30)));

        assertThat(result.providerId()).isEqualTo("groq");
        server.verify();
    }

    @Test
    @DisplayName("AI-03: task chưa cấu hình ⇒ không dùng được")
    void unconfiguredTaskIsUnavailable() {
        ProviderChainAiGateway gateway = gateway(publicChain());

        assertThat(gateway.isAvailable(AiTask.MENU_DAY_FIX)).isFalse();
        assertThat(gateway.taskConfig(AiTask.MENU_DAY_FIX)).isEmpty();
        assertThat(gateway.taskConfig(AiTask.MENU_WEEKLY)).isPresent();
    }

    @Test
    @DisplayName("AI-03: hết deadline ⇒ TIMEOUT, không gọi provider")
    void stopsWhenDeadlinePassed() {
        server.expect(never(), requestTo(GEMINI_URL));

        assertThatThrownBy(() -> gateway(publicChain()).complete(request(NOW.minusSeconds(1))))
                .extracting(ex -> ((AiClientException) ex).getReason())
                .isEqualTo(AiErrorReason.TIMEOUT);
        server.verify();
    }

    @Test
    @DisplayName("AI-03: read timeout mỗi lần gọi không vượt thời gian còn lại tới deadline")
    void boundsReadTimeoutByRemainingDeadline() {
        server.expect(once(), requestTo(GEMINI_URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        gateway(publicChain()).complete(request(NOW.plusSeconds(5)));

        assertThat(readTimeouts).containsExactly(Duration.ofSeconds(5));
    }

    private ProviderChainAiGateway gateway(AiProperties properties) {
        return new ProviderChainAiGateway(
                properties,
                config -> new OpenAiCompatibleProvider(config, readTimeout -> {
                    readTimeouts.add(readTimeout);
                    return builder.clone().baseUrl(config.baseUrl()).build();
                }),
                CLOCK);
    }

    private static AiProperties publicChain() {
        return new AiProperties(true, providers(), Map.of(AiTask.MENU_WEEKLY, publicTask()));
    }

    private static List<AiProperties.Provider> providers() {
        return List.of(provider("gemini", true), provider("groq", false));
    }

    private static AiProperties.Provider provider(String id, boolean mayTrain) {
        return new AiProperties.Provider(
                id, "http://" + id + ".test", "model-" + id, "key-" + id, mayTrain, null, Duration.ofSeconds(30), null);
    }

    private static AiProperties.TaskConfig publicTask() {
        return new AiProperties.TaskConfig(List.of("gemini", "groq"), AiDataClass.PUBLIC_CATALOG, null, 2);
    }

    private static AiRequest request(Instant deadline) {
        return new AiRequest(AiTask.MENU_WEEKLY, "menu/weekly_v1", "SYS", "USER", deadline);
    }
}
