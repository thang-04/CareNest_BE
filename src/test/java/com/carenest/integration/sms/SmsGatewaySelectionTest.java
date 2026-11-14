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
    @DisplayName("SMS: provider=textbee có API key ⇒ chỉ có TextbeeSmsGateway")
    void selectsTextbeeWhenConfigured() {
        runner.withPropertyValues("carenest.sms.provider=textbee", "carenest.sms.textbee.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsGateway.class);
                    assertThat(context).getBean(SmsGateway.class).isInstanceOf(TextbeeSmsGateway.class);
                });
    }

    @Test
    @DisplayName("SMS: provider=textbee thiếu API key ⇒ không khởi động được")
    void textbeeWithoutApiKeyFailsStartup() {
        runner.withPropertyValues("carenest.sms.provider=textbee").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("TEXTBEE_API_KEY");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SmsProperties.class)
    @Import({FakeSmsGateway.class, TextbeeSmsGateway.class})
    static class SmsConfig {}
}
