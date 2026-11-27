package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
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
    @DisplayName("CP-21: cấp lại mật khẩu đi qua nhà cung cấp SMS thật")
    void passwordResetUsesConfiguredProvider() {
        var sender =
                new ParentSmsSender(smsGateway, new SmsProperties(SmsProperties.TEXTBEE, null), logRepository, clock);
        assertThat(sender.sendPasswordReset(parent(), "Reset-Test-Only")).isEqualTo(SmsStatus.SENT);
        verify(smsGateway).send(eq("0912345678"), org.mockito.ArgumentMatchers.contains("Reset-Test-Only"));
        verify(logRepository)
                .save(org.mockito.ArgumentMatchers.argThat(
                        log -> log.getPurpose() == com.carenest.utils.enums.SmsPurpose.PASSWORD_RESET
                                && log.getStatus() == SmsStatus.SENT));
    }

    @Test
    @DisplayName("CP-21: SMS lỗi trả FAILED và không ghi nội dung mật khẩu vào log")
    void passwordResetReportsDeliveryFailure() {
        doThrow(new IllegalStateException("SMS unavailable")).when(smsGateway).send(anyString(), anyString());
        var sender =
                new ParentSmsSender(smsGateway, new SmsProperties(SmsProperties.TEXTBEE, null), logRepository, clock);
        assertThat(sender.sendPasswordReset(parent(), "Reset-Test-Only")).isEqualTo(SmsStatus.FAILED);
        verify(logRepository).save(org.mockito.ArgumentMatchers.argThat(log -> log.getStatus() == SmsStatus.FAILED));
    }

    @Test
    @DisplayName("AUTH-18: bật nhà cung cấp thật ⇒ tin có mật khẩu tạm không qua nhà cung cấp")
    void realProviderNeverReceivesTemporaryPassword() {
        var sender =
                new ParentSmsSender(smsGateway, new SmsProperties(SmsProperties.TEXTBEE, null), logRepository, clock);

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
