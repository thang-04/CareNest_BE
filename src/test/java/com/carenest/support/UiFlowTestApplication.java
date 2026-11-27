package com.carenest.support;

import com.carenest.CareNestApplication;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/** Chạy UI thật với ngày nghiệp vụ kiểm soát được, chỉ trên bản sao DB local dành riêng cho lượt thử. */
public final class UiFlowTestApplication {
    private UiFlowTestApplication() {}

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CareNestApplication.class, FixedUiClock.class);
        app.setAdditionalProfiles("dev");
        app.run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedUiClock {
        @Bean
        @Primary
        Clock uiFlowClock(Environment environment) {
            String url = environment.getRequiredProperty("spring.datasource.url");
            if (!url.equals("jdbc:postgresql://localhost:5434/carenest_ui_20261122")) {
                throw new IllegalStateException("UI test clock requires the isolated local UI database");
            }
            return Clock.fixed(
                    Instant.parse(environment.getRequiredProperty("CARENEST_UI_TEST_NOW")),
                    ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
}
