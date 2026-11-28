package com.carenest.config;

import java.time.Clock;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

// "Hôm nay" (phân công/enrollment còn hiệu lực) tính theo múi giờ trường, không theo múi giờ máy chủ
@Slf4j
@Configuration
@EnableScheduling
public class ClockConfig {

    public static final String SYSTEM_CLOCK = "systemClock";

    // Clock nghiệp vụ: theo giờ giả lập của request khi bật dev clock, còn lại là giờ thật
    @Bean
    public Clock clock(@Value("${carenest.time-zone}") String timeZone) {
        return new RequestAwareClock(Clock.system(ZoneId.of(timeZone)));
    }

    // Luôn giờ thật: phiên đăng nhập, OTP, token Keycloak không được lệch theo giờ giả lập.
    // defaultCandidate=false: chỉ inject khi ghi rõ @Qualifier, inject Clock thường vẫn nhận clock nghiệp vụ
    @Bean(name = SYSTEM_CLOCK, defaultCandidate = false)
    public Clock systemClock(@Value("${carenest.time-zone}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }

    @Bean
    @ConditionalOnProperty(name = "carenest.dev.clock.enabled", havingValue = "true")
    public FilterRegistrationBean<DevClockFilter> devClockFilter() {
        log.warn(
                "[ClockConfig]|devClockFilter|Dev clock ENABLED: header {} overrides business time",
                DevClockFilter.HEADER);
        FilterRegistrationBean<DevClockFilter> registration = new FilterRegistrationBean<>(new DevClockFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
