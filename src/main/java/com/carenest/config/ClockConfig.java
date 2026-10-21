package com.carenest.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// "Hôm nay" (phân công/enrollment còn hiệu lực) tính theo múi giờ trường, không theo múi giờ máy chủ
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${carenest.time-zone}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
