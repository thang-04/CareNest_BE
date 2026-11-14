package com.carenest.integration.sms;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gửi SMS qua textbee.dev: điện thoại Android cài app textbee gửi tin bằng SIM của nó (chỉ dev/demo). Bật khi
 * {@code carenest.sms.provider=textbee}. Không log SĐT, nội dung tin, API key; lý do lỗi đã che dãy số dài.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "carenest.sms.provider", havingValue = SmsProperties.TEXTBEE)
public class TextbeeSmsGateway implements SmsGateway {
    static final String DEFAULT_BASE_URL = "https://api.textbee.dev/api/v1";
    static final String SEND_PATH = "/gateway/send-sms";
    private static final Pattern VN_PHONE = Pattern.compile("0\\d{9}");
    private static final Pattern REASON = Pattern.compile("\"(?:error|message)\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\+?\\d{6,}");

    private final SmsProperties.Textbee textbee;
    private final RestClient client;

    @Autowired
    public TextbeeSmsGateway(SmsProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(requireComplete(properties))));
    }

    TextbeeSmsGateway(SmsProperties properties, RestClient.Builder builder) {
        this.textbee = requireComplete(properties);
        String baseUrl = hasText(textbee.baseUrl()) ? textbee.baseUrl().trim() : DEFAULT_BASE_URL;
        this.client = builder.baseUrl(baseUrl)
                .defaultHeader("x-api-key", textbee.apiKey())
                .build();
    }

    @Override
    public void send(String phone, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipients", List.of(toE164(phone)));
        body.put("message", text);
        // Trống ⇒ textbee chọn thiết bị mặc định / thiết bị có heartbeat gần nhất
        if (hasText(textbee.deviceId())) body.put("deviceId", textbee.deviceId().trim());
        try {
            client.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String reason = reason(ex.getResponseBodyAsString());
            log.warn("[TextbeeSmsGateway]|send|status={}|reason={}|FAILED", status, reason);
            // Không giữ cause: thân lỗi có thể chứa SĐT người nhận
            throw new SmsDeliveryException("textbee từ chối gửi SMS: status=" + status + ", reason=" + reason);
        } catch (RestClientException ex) {
            log.warn("[TextbeeSmsGateway]|send|type={}|FAILED", ex.getClass().getSimpleName());
            throw new SmsDeliveryException(
                    "Không gọi được textbee: " + ex.getClass().getSimpleName());
        }
        // 200 = textbee nhận vào hàng đợi; điện thoại gửi sau, chưa phải đã tới máy người nhận
        log.info("[TextbeeSmsGateway]|send|phone=***{}|QUEUED", phone.substring(phone.length() - 2));
    }

    /** SĐT nội bộ 10 số bắt đầu 0 ⇒ E.164 +84 (textbee không tự thêm mã quốc gia). */
    static String toE164(String phone) {
        if (phone == null || !VN_PHONE.matcher(phone).matches()) {
            throw new SmsDeliveryException("SĐT không đúng định dạng 10 số bắt đầu 0");
        }
        return "+84" + phone.substring(1);
    }

    // Lý do lỗi textbee (vd "No enabled device found") giúp gỡ lỗi; che mọi dãy số dài phòng có SĐT trong đó
    static String reason(String body) {
        Matcher matcher = REASON.matcher(body == null ? "" : body);
        return matcher.find() ? LONG_DIGITS.matcher(matcher.group(1)).replaceAll("***") : "unknown";
    }

    // Chọn textbee mà thiếu API key ⇒ dừng lúc khởi động, không đợi tới lần gửi đầu
    private static SmsProperties.Textbee requireComplete(SmsProperties properties) {
        SmsProperties.Textbee textbee = properties.textbee();
        if (textbee == null || !hasText(textbee.apiKey())) {
            throw new IllegalStateException("CARENEST_SMS_PROVIDER=textbee nhưng thiếu cấu hình: TEXTBEE_API_KEY");
        }
        return textbee;
    }

    private static SimpleClientHttpRequestFactory requestFactory(SmsProperties.Textbee textbee) {
        var factory = new SimpleClientHttpRequestFactory();
        if (textbee.connectTimeout() != null) factory.setConnectTimeout(textbee.connectTimeout());
        if (textbee.readTimeout() != null) factory.setReadTimeout(textbee.readTimeout());
        return factory;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
