package com.carenest.controller.common;

import com.carenest.config.ClockConfig;
import com.carenest.config.RequestAwareClock;
import com.carenest.dto.common.DevClockResponse;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** CHỈ DÙNG KHI DEV/KIỂM THỬ: tester xem server đang tính nghiệp vụ theo giờ nào. Không đăng ký ở production. */
@RestController
@Slf4j
@ConditionalOnProperty(name = "carenest.dev.clock.enabled", havingValue = "true")
public class DevClockController {
    private static final String LOG_PREFIX = "[DevClockController]";

    private final Clock clock;
    private final Clock systemClock;

    public DevClockController(Clock clock, @Qualifier(ClockConfig.SYSTEM_CLOCK) Clock systemClock) {
        this.clock = clock;
        this.systemClock = systemClock;
    }

    @GetMapping("/dev/clock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseJson<DevClockResponse>> getClock() {
        String logPrefix = LOG_PREFIX + "|getClock";
        log.info("{}|START", logPrefix);
        DevClockResponse response = new DevClockResponse(
                OffsetDateTime.now(systemClock),
                OffsetDateTime.now(clock),
                RequestAwareClock.simulatedInstant().isPresent());
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get dev clock success", response);
    }
}
