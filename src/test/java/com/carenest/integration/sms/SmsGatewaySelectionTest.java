package com.carenest.integration.sms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class SmsGatewaySelectionTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SmsConfig.class);

    @Test
    @DisplayName("SMS: không đặt provider ⇒ chỉ có cổng giả")
    void defaultsToFakeGateway() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SmsGateway.class);
            assertThat(context).getBean(SmsGateway.class).isInstanceOf(FakeSmsGateway.class);
        });
    }

    @Test
    @DisplayName("SMS: provider=twilio đủ cấu hình ⇒ chỉ có TwilioSmsGateway")
    void selectsTwilioWhenConfigured() {
        runner.withPropertyValues(
                        "carenest.sms.provider=twilio",
                        "carenest.sms.twilio.account-sid=ACtest",
                        "carenest.sms.twilio.auth-token=test-token",
                        "carenest.sms.twilio.from=+15005550006")
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsGateway.class);
                    assertThat(context).getBean(SmsGateway.class).isInstanceOf(TwilioSmsGateway.class);
                });
    }

    @Test
    @DisplayName("SMS: provider=twilio thiếu cấu hình ⇒ không khởi động được")
    void twilioWithoutSecretsFailsStartup() {
        runner.withPropertyValues("carenest.sms.provider=twilio").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("TWILIO_ACCOUNT_SID");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SmsProperties.class)
    @Import({FakeSmsGateway.class, TwilioSmsGateway.class})
    static class SmsConfig {}
}
