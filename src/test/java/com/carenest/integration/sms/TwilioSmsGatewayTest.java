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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TwilioSmsGatewayTest {

    private static final String SID = "AC0000000000000000000000000000test";
    private static final String TOKEN = "test-auth-token";
    private static final String MESSAGES_URL = "https://api.twilio.com/2010-04-01/Accounts/" + SID + "/Messages.json";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    @DisplayName("Twilio: gửi form To E.164, From, Body kèm Basic auth")
    void postsFormWithBasicAuthAndE164Phone() {
        String basic = Base64.getEncoder().encodeToString((SID + ":" + TOKEN).getBytes(StandardCharsets.UTF_8));
        server.expect(once(), requestTo(MESSAGES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + basic))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content()
                        .formDataContains(
                                Map.of("To", "+84912345678", "From", "+15005550006", "Body", "Ma kich hoat: 123456")))
                .andRespond(withStatus(HttpStatus.CREATED));

        gateway(twilio("+15005550006", null)).send("0912345678", "Ma kich hoat: 123456");

        server.verify();
    }

    @Test
    @DisplayName("Twilio: có Messaging Service SID thì gửi qua service thay số gửi")
    void usesMessagingServiceWhenConfigured() {
        server.expect(once(), requestTo(MESSAGES_URL))
                .andExpect(content().formDataContains(Map.of("MessagingServiceSid", "MG123")))
                .andRespond(withStatus(HttpStatus.CREATED));

        gateway(twilio("+15005550006", "MG123")).send("0912345678", "x");

        server.verify();
    }

    @Test
    @DisplayName("Twilio: lỗi 4xx ⇒ SmsDeliveryException chỉ mang status và mã lỗi, không SĐT")
    void rejectionCarriesOnlyStatusAndTwilioCode() {
        server.expect(once(), requestTo(MESSAGES_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":21608,\"message\":\"The number +84912345678 is unverified\"}"));

        assertThatThrownBy(() -> gateway(twilio("+15005550006", null)).send("0912345678", "x"))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("status=400")
                .hasMessageContaining("code=21608")
                .hasNoCause()
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("912345678"));
    }

    @Test
    @DisplayName("Twilio: lỗi 5xx ⇒ SmsDeliveryException")
    void serverErrorThrowsDeliveryException() {
        server.expect(once(), requestTo(MESSAGES_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> gateway(twilio("+15005550006", null)).send("0912345678", "x"))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("status=503")
                .hasMessageContaining("code=unknown");
    }

    @Test
    @DisplayName("Twilio: timeout ⇒ SmsDeliveryException")
    void timeoutThrowsDeliveryException() {
        server.expect(once(), requestTo(MESSAGES_URL)).andRespond(withException(new SocketTimeoutException()));

        assertThatThrownBy(() -> gateway(twilio("+15005550006", null)).send("0912345678", "x"))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("Không gọi được Twilio");
    }

    @Test
    @DisplayName("Twilio: SĐT sai định dạng ⇒ không gọi mạng")
    void invalidPhoneNeverCallsTwilio() throws IOException {
        server.expect(never(), anything());

        assertThatThrownBy(() -> gateway(twilio("+15005550006", null)).send("84912345678", "x"))
                .isInstanceOf(SmsDeliveryException.class);
        server.verify();
    }

    @Test
    @DisplayName("Twilio: thiếu SID/token/số gửi ⇒ báo đủ tên biến thiếu")
    void missingConfigurationFailsFast() {
        var empty = new SmsProperties(SmsProperties.TWILIO, new SmsProperties.Twilio("", null, " ", null, null, null));

        assertThatThrownBy(() -> new TwilioSmsGateway(empty, builder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TWILIO_ACCOUNT_SID")
                .hasMessageContaining("TWILIO_AUTH_TOKEN")
                .hasMessageContaining("TWILIO_FROM hoặc TWILIO_MESSAGING_SERVICE_SID");
    }

    @Test
    @DisplayName("Twilio: toString không lộ auth token")
    void toStringHidesAuthToken() {
        assertThat(twilio("+15005550006", null).twilio().toString()).doesNotContain(TOKEN);
    }

    private TwilioSmsGateway gateway(SmsProperties properties) {
        return new TwilioSmsGateway(properties, builder);
    }

    private static SmsProperties twilio(String from, String messagingServiceSid) {
        return new SmsProperties(
                SmsProperties.TWILIO,
                new SmsProperties.Twilio(
                        SID, TOKEN, from, messagingServiceSid, Duration.ofSeconds(5), Duration.ofSeconds(10)));
    }
}
