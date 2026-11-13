package com.carenest.integration.sms;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gửi SMS qua Twilio Messages REST API (không dùng SDK). Bật khi {@code carenest.sms.provider=twilio}. Không log SĐT,
 * nội dung tin, token; lỗi chỉ mang HTTP status và mã lỗi Twilio.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "carenest.sms.provider", havingValue = SmsProperties.TWILIO)
public class TwilioSmsGateway implements SmsGateway {
    static final String BASE_URL = "https://api.twilio.com";
    static final String MESSAGES_PATH = "/2010-04-01/Accounts/{sid}/Messages.json";
    private static final Pattern VN_PHONE = Pattern.compile("0\\d{9}");
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");

    private final SmsProperties.Twilio twilio;
    private final RestClient client;

    @Autowired
    public TwilioSmsGateway(SmsProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(requireComplete(properties))));
    }

    TwilioSmsGateway(SmsProperties properties, RestClient.Builder builder) {
        this.twilio = requireComplete(properties);
        this.client = builder.baseUrl(BASE_URL)
                .defaultHeaders(headers -> headers.setBasicAuth(twilio.accountSid(), twilio.authToken()))
                .build();
    }

    @Override
    public void send(String phone, String text) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("To", toE164(phone));
        form.add("Body", text);
        if (hasText(twilio.messagingServiceSid())) {
            form.add("MessagingServiceSid", twilio.messagingServiceSid());
        } else {
            form.add("From", twilio.from());
        }
        try {
            client.post()
                    .uri(MESSAGES_PATH, twilio.accountSid())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String code = twilioErrorCode(ex.getResponseBodyAsString());
            log.warn("[TwilioSmsGateway]|send|status={}|twilioCode={}|FAILED", status, code);
            // Không giữ cause: thân lỗi Twilio có thể chứa SĐT người nhận
            throw new SmsDeliveryException("Twilio từ chối gửi SMS: status=" + status + ", code=" + code);
        } catch (RestClientException ex) {
            log.warn("[TwilioSmsGateway]|send|type={}|FAILED", ex.getClass().getSimpleName());
            throw new SmsDeliveryException(
                    "Không gọi được Twilio: " + ex.getClass().getSimpleName());
        }
        log.info("[TwilioSmsGateway]|send|phone=***{}|SENT", phone.substring(phone.length() - 2));
    }

    /** SĐT nội bộ 10 số bắt đầu 0 ⇒ E.164 +84. */
    static String toE164(String phone) {
        if (phone == null || !VN_PHONE.matcher(phone).matches()) {
            throw new SmsDeliveryException("SĐT không đúng định dạng 10 số bắt đầu 0");
        }
        return "+84" + phone.substring(1);
    }

    private static String twilioErrorCode(String body) {
        Matcher matcher = ERROR_CODE.matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    // Chọn twilio mà thiếu cấu hình ⇒ dừng lúc khởi động, không đợi tới lần gửi đầu
    private static SmsProperties.Twilio requireComplete(SmsProperties properties) {
        SmsProperties.Twilio twilio = properties.twilio();
        List<String> missing = new ArrayList<>();
        if (twilio == null || !hasText(twilio.accountSid())) missing.add("TWILIO_ACCOUNT_SID");
        if (twilio == null || !hasText(twilio.authToken())) missing.add("TWILIO_AUTH_TOKEN");
        if (twilio == null || (!hasText(twilio.from()) && !hasText(twilio.messagingServiceSid()))) {
            missing.add("TWILIO_FROM hoặc TWILIO_MESSAGING_SERVICE_SID");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "CARENEST_SMS_PROVIDER=twilio nhưng thiếu cấu hình: " + String.join(", ", missing));
        }
        return twilio;
    }

    private static SimpleClientHttpRequestFactory requestFactory(SmsProperties.Twilio twilio) {
        var factory = new SimpleClientHttpRequestFactory();
        if (twilio.connectTimeout() != null) factory.setConnectTimeout(twilio.connectTimeout());
        if (twilio.readTimeout() != null) factory.setReadTimeout(twilio.readTimeout());
        return factory;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
