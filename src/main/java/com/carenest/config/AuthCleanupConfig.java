package com.carenest.config;

import com.carenest.service.account.AuthService;
import com.carenest.service.account.AuthStateStore;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class AuthCleanupConfig {
    private final AuthProperties properties;
    private final AuthStateStore state;
    private final AuthService authService;
    private final Clock clock;

    public AuthCleanupConfig(
            AuthProperties properties,
            AuthStateStore state,
            AuthService authService,
            @Qualifier(ClockConfig.SYSTEM_CLOCK) Clock clock) {
        this.properties = properties;
        this.state = state;
        this.authService = authService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void removeExpiredAuthState() {
        if (!properties.enabled()) return;
        state.cleanup(clock.instant());
        authService.expireTakeovers();
    }
}
