package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.entity.account.UserAccount;
import com.carenest.integration.sms.SmsGateway;
import com.carenest.integration.sms.SmsProperties;
import com.carenest.repository.account.ParentSmsLogRepository;
import com.carenest.utils.enums.SmsStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParentSmsSenderTest {

    private final SmsGateway smsGateway = mock(SmsGateway.class);
    private final ParentSmsLogRepository logRepository = mock(ParentSmsLogRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("AUTH-18: bật nhà cung cấp thật ⇒ tin có mật khẩu tạm không qua nhà cung cấp")
    void realProviderNeverReceivesTemporaryPassword() {
        var sender =
                new ParentSmsSender(smsGateway, new SmsProperties(SmsProperties.TWILIO, null), logRepository, clock);

        SmsStatus status = sender.sendActivation(parent(), "Temp-Pass-1");

        assertThat(status).isEqualTo(SmsStatus.SENT);
        verifyNoInteractions(smsGateway);
    }

    @Test
    @DisplayName("AUTH-18: cổng giả ⇒ tin kích hoạt vẫn đi qua SmsGateway như trước")
    void fakeProviderKeepsCurrentBehaviour() {
        var sender = new ParentSmsSender(smsGateway, new SmsProperties(SmsProperties.FAKE, null), logRepository, clock);

        sender.sendActivation(parent(), "Temp-Pass-1");

        verify(smsGateway).send(eq("0912345678"), anyString());
    }

    private static UserAccount parent() {
        UserAccount account = mock(UserAccount.class);
        given(account.getUsername()).willReturn("0912345678");
        return account;
    }
}
