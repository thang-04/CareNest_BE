package com.carenest.integration.push;

import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chọn cổng đẩy: có file khóa Firebase ⇒ FCM; không có (máy dev, test) ⇒ chỉ ghi log (FR-009). */
@Configuration
class PushConfig {

    @Bean
    PushGateway pushGateway(@Value("${carenest.push.fcm.credentials-file:}") String credentialsFile, Clock clock) {
        return credentialsFile == null || credentialsFile.isBlank()
                ? new LoggingPushGateway()
                : new FcmPushGateway(Path.of(credentialsFile), clock);
    }
}
