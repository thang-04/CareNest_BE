package com.carenest.integration.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TextbeeSmsGatewayTest {

    private static final String API_KEY = "test-api-key";
    private static final String SEND_URL = "https://api.textbee.dev/api/v1/gateway/send-sms";
    private static final String QUEUED = "{\"data\":{\"success\":true,\"smsBatchId\":\"b1\",\"recipientCount\":1}}";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    @DisplayName("textbee: gửi JSON recipients E.164, message, deviceId kèm x-api-key")
    void postsJsonWithApiKeyAndE164Phone() {
        server.expect(once(), requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", API_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content()
                        .json(
                                "{\"recipients\":[\"+84912345678\"],\"message\":\"Ma kich hoat: 123456\",\"deviceId\":\"dev-1\"}",
                                org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess(QUEUED, MediaType.APPLICATION_JSON));

        gateway(textbee(null, "dev-1")).send("0912345678", "Ma kich hoat: 123456");

        server.verify();
    }

    @Test
    @DisplayName("textbee: không đặt deviceId thì bỏ trường, base URL tùy chỉnh được")
    void omitsDeviceIdAndHonoursBaseUrl() {
        server.expect(once(), requestTo("http://textbee.local/api/v1/gateway/send-sms"))
                .andExpect(content()
                        .json(
                                "{\"recipients\":[\"+84912345678\"],\"message\":\"x\"}",
                                org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess(QUEUED, MediaType.APPLICATION_JSON));

        gateway(textbee("http://textbee.local/api/v1", " ")).send("0912345678", "x");

        server.verify();
    }

    @Test
    @DisplayName("textbee: lỗi 4xx ⇒ SmsDeliveryException mang status và lý do đã che SĐT")
    void rejectionCarriesStatusAndMaskedReason() {
        server.expect(once(), requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":false,\"error\":\"Invalid recipient +84912345678\"}"));

        assertThatThrownBy(() -> gateway(textbee(null, null)).send("0912345678", "x"))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("status=400")
                .hasMessageContaining("reason=Invalid recipient ***")
                .hasNoCause()
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("912345678"));
    }

    @Test
    @DisplayName("textbee: 429 hết hạn mức ⇒ lấy lý do từ trường message")
    void quotaErrorReadsMessageField() {
        server.expect(once(), requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Daily limit reached\"}"));

        assertThatThrownBy(() -> gateway(textbee(null, null)).send("0912345678", "x"))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("status=429")
                .hasMessageContaining("reason=Daily limit reached");
    }

    @Test
    @DisplayName("textbee: lỗi 5xx không có thân ⇒ SmsDeliveryException reason=unknown")
    void serverErrorThrowsDeliveryException() {
        server.expect(once(), requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> gateway(textbee(null, null)).send("0912345678", "x"))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("status=503")
                .hasMessageContaining("reason=unknown");
    }

    @Test
    @DisplayName("textbee: timeout ⇒ SmsDeliveryException")
    void timeoutThrowsDeliveryException() {
        server.expect(once(), requestTo(SEND_URL)).andRespond(withException(new SocketTimeoutException()));

        assertThatThrownBy(() -> gateway(textbee(null, null)).send("0912345678", "x"))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("Không gọi được textbee");
    }

    @Test
    @DisplayName("textbee: SĐT sai định dạng ⇒ không gọi mạng")
    void invalidPhoneNeverCallsTextbee() {
        server.expect(never(), anything());

        assertThatThrownBy(() -> gateway(textbee(null, null)).send("84912345678", "x"))
                .isInstanceOf(SmsDeliveryException.class);
        server.verify();
    }

    @Test
    @DisplayName("textbee: thiếu API key ⇒ báo tên biến thiếu")
    void missingApiKeyFailsFast() {
        var empty = new SmsProperties(SmsProperties.TEXTBEE, new SmsProperties.Textbee(null, " ", null, null, null));

        assertThatThrownBy(() -> new TextbeeSmsGateway(empty, builder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEXTBEE_API_KEY");
    }

    @Test
    @DisplayName("textbee: toString không lộ API key")
    void toStringHidesApiKey() {
        assertThat(textbee(null, "dev-1").textbee().toString()).doesNotContain(API_KEY);
    }

    private TextbeeSmsGateway gateway(SmsProperties properties) {
        return new TextbeeSmsGateway(properties, builder);
    }

    private static SmsProperties textbee(String baseUrl, String deviceId) {
        return new SmsProperties(
                SmsProperties.TEXTBEE,
                new SmsProperties.Textbee(baseUrl, API_KEY, deviceId, Duration.ofSeconds(5), Duration.ofSeconds(10)));
    }
}
