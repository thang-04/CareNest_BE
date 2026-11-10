package com.carenest.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.carenest.utils.enums.AiErrorReason;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExternalEmbeddingClientTest {

    private static final String EMBED_URL = "http://embed.test/v1/embeddings";

    private MockRestServiceServer server;
    private ExternalEmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // batchSize = 2, dimensions = 3 (xem ExternalAiClientTest.properties)
        client = new ExternalEmbeddingClient(ExternalAiClientTest.properties("http://llm.test/v1"), builder);
    }

    @Test
    void splitsIntoBatchesAndKeepsInputOrder() {
        server.expect(once(), requestTo(EMBED_URL))
                .andExpect(header("Authorization", "Bearer embed-key"))
                .andExpect(jsonPath("$.model").value("embed-model"))
                .andExpect(jsonPath("$.dimensions").value(3))
                .andExpect(jsonPath("$.input.length()").value(2))
                .andRespond(withSuccess("""
                        {"data":[{"index":1,"embedding":[0.2,0.2,0.2]},{"index":0,"embedding":[0.1,0.1,0.1]}]}""", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(EMBED_URL))
                .andExpect(jsonPath("$.input.length()").value(1))
                .andRespond(withSuccess("""
                        {"data":[{"index":0,"embedding":[0.3,0.3,0.3]}]}""", MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embedBatch(List.of("a", "b", "c"));

        assertThat(vectors).hasSize(3);
        assertThat(vectors.get(0)[0]).isEqualTo(0.1f);
        assertThat(vectors.get(1)[0]).isEqualTo(0.2f);
        assertThat(vectors.get(2)[0]).isEqualTo(0.3f);
        server.verify();
    }

    @Test
    void keepsResponseOrderWhenProviderOmitsIndex() {
        server.expect(once(), requestTo(EMBED_URL)).andRespond(withSuccess("""
                        {"data":[{"index":null,"embedding":[0.1,0.1,0.1]},{"embedding":[0.2,0.2,0.2]}]}""", MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embedBatch(List.of("a", "b"));

        assertThat(vectors.get(0)[0]).isEqualTo(0.1f);
        assertThat(vectors.get(1)[0]).isEqualTo(0.2f);
    }

    @Test
    void rejectsWrongDimensions() {
        server.expect(once(), requestTo(EMBED_URL)).andRespond(withSuccess("""
                        {"data":[{"index":0,"embedding":[0.1,0.1]}]}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("a"))
                .isInstanceOf(AiClientException.class)
                .extracting(ex -> ((AiClientException) ex).getReason())
                .isEqualTo(AiErrorReason.OUTPUT_INVALID);
    }

    @Test
    void mapsServerErrorToProviderUnavailable() {
        server.expect(once(), requestTo(EMBED_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.embed("a"))
                .isInstanceOf(AiClientException.class)
                .extracting(ex -> ((AiClientException) ex).getReason())
                .isEqualTo(AiErrorReason.PROVIDER_UNAVAILABLE);
    }
}
