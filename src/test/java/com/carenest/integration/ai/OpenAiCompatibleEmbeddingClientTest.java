package com.carenest.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.carenest.utils.enums.AiErrorReason;
import java.time.Duration;
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

class OpenAiCompatibleEmbeddingClientTest {

    private static final String URL = "http://embed.test/embeddings";

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private OpenAiCompatibleEmbeddingClient client(boolean enabled, EmbeddingProperties properties) {
        return new OpenAiCompatibleEmbeddingClient(
                new AiProperties(enabled, List.of(), Map.of()),
                properties,
                readTimeout -> builder.baseUrl("http://embed.test").build());
    }

    private static EmbeddingProperties properties(Integer dimensions, Integer batchSize) {
        return new EmbeddingProperties(
                "http://embed.test", "embed-model", "key-embed", dimensions, null, null, batchSize, null);
    }

    @Test
    @DisplayName("AI-06: gửi model và input theo lô, trả vector đúng thứ tự input kể cả khi response đảo index")
    void returnsVectorsInInputOrder() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer key-embed"))
                .andExpect(jsonPath("$.model").value("embed-model"))
                .andExpect(jsonPath("$.input[0]").value("a"))
                .andExpect(jsonPath("$.input[1]").value("b"))
                .andRespond(withSuccess("""
                        {"data":[{"index":1,"embedding":[0.0,1.0]},{"index":0,"embedding":[1.0,0.0]}]}
                        """, MediaType.APPLICATION_JSON));

        List<float[]> vectors = client(true, properties(null, null)).embed(List.of("a", "b"), Duration.ofSeconds(5));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0f, 0.0f);
        assertThat(vectors.get(1)).containsExactly(0.0f, 1.0f);
        server.verify();
    }

    @Test
    @DisplayName("Gemini không trả index ⇒ giữ thứ tự response; batch-size nhỏ hơn số input ⇒ tách nhiều lần gọi")
    void splitsIntoBatchesAndAcceptsMissingIndex() {
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.input.length()").value(2))
                .andRespond(withSuccess("""
                        {"data":[{"embedding":[1.0]},{"embedding":[2.0]}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.input.length()").value(1))
                .andRespond(withSuccess("""
                        {"data":[{"embedding":[3.0]}]}
                        """, MediaType.APPLICATION_JSON));

        List<float[]> vectors = client(true, properties(null, 2)).embed(List.of("a", "b", "c"), Duration.ofSeconds(5));

        assertThat(vectors).extracting(v -> v[0]).containsExactly(1.0f, 2.0f, 3.0f);
        server.verify();
    }

    @Test
    @DisplayName("AI-03: AI tắt hoặc thiếu cấu hình ⇒ không khả dụng, không gọi mạng")
    void unavailableWhenDisabledOrNotConfigured() {
        OpenAiCompatibleEmbeddingClient disabled = client(false, properties(null, null));
        OpenAiCompatibleEmbeddingClient noKey = client(
                true, new EmbeddingProperties("http://embed.test", "embed-model", "", null, null, null, null, null));

        assertThat(disabled.isAvailable()).isFalse();
        assertThat(noKey.isAvailable()).isFalse();
        assertThatThrownBy(() -> disabled.embed(List.of("a"), Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(
                        AiClientException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiErrorReason.DISABLED));
        server.verify();
    }

    @Test
    @DisplayName("429 ⇒ RATE_LIMITED; 500 ⇒ PROVIDER_UNAVAILABLE")
    void mapsHttpErrors() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        OpenAiCompatibleEmbeddingClient client = client(true, properties(null, null));

        assertThatThrownBy(() -> client.embed(List.of("a"), Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(
                        AiClientException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiErrorReason.RATE_LIMITED));
        assertThatThrownBy(() -> client.embed(List.of("a"), Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(
                        AiClientException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiErrorReason.PROVIDER_UNAVAILABLE));
    }

    @Test
    @DisplayName("Số vector không khớp input hoặc sai số chiều cấu hình ⇒ OUTPUT_INVALID")
    void rejectsMismatchedResponses() {
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("""
                        {"data":[{"embedding":[1.0]}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.dimensions").value(3))
                .andRespond(withSuccess("""
                        {"data":[{"embedding":[1.0,2.0]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(true, properties(null, null)).embed(List.of("a", "b"), Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(
                        AiClientException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiErrorReason.OUTPUT_INVALID));
        assertThatThrownBy(() -> client(true, properties(3, null)).embed(List.of("a"), Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(
                        AiClientException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiErrorReason.OUTPUT_INVALID));
    }

    @Test
    @DisplayName("Hết ngân sách thời gian ⇒ TIMEOUT, không gọi mạng")
    void exhaustedBudgetTimesOutWithoutCall() {
        assertThatThrownBy(() -> client(true, properties(null, null)).embed(List.of("a"), Duration.ZERO))
                .isInstanceOfSatisfying(
                        AiClientException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiErrorReason.TIMEOUT));
        server.verify();
    }
}
