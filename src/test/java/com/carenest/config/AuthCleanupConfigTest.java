package com.carenest.config;

import static org.mockito.Mockito.*;

import com.carenest.service.account.AuthStateStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthCleanupConfigTest {
    @Test
    void scheduledCleanupUsesCurrentTimeOnlyWhenGatewayEnabled() {
        var properties = mock(AuthProperties.class);
        var store = mock(AuthStateStore.class);
        Instant now = Instant.parse("2026-10-12T00:00:00Z");
        var cleanup = new AuthCleanupConfig(properties, store, Clock.fixed(now, ZoneOffset.UTC));
        cleanup.removeExpiredAuthState();
        verifyNoInteractions(store);
        when(properties.enabled()).thenReturn(true);
        cleanup.removeExpiredAuthState();
        verify(store).cleanup(now);
    }
}
