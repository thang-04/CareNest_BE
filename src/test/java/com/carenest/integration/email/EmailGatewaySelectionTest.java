package com.carenest.integration.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class EmailGatewaySelectionTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(EmailConfig.class);

    @Test
    @DisplayName("Email: không đặt provider ⇒ chỉ có cổng log")
    void defaultsToLoggingGateway() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(EmailGateway.class);
            assertThat(context).getBean(EmailGateway.class).isInstanceOf(LoggingEmailGateway.class);
        });
    }

    @Test
    @DisplayName("Email: provider=smtp đủ cấu hình Gmail ⇒ chỉ có SmtpEmailGateway")
    void selectsSmtpWhenConfigured() {
        runner.withPropertyValues(
                        "carenest.email.provider=smtp",
                        "carenest.email.smtp.host=smtp.gmail.com",
                        "carenest.email.smtp.port=587",
                        "carenest.email.smtp.username=team@example.com",
                        "carenest.email.smtp.password=app-password",
                        "carenest.email.smtp.starttls=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailGateway.class);
                    assertThat(context).getBean(EmailGateway.class).isInstanceOf(SmtpEmailGateway.class);
                });
    }

    @Test
    @DisplayName("Email: provider=smtp có username thiếu App Password ⇒ không khởi động được")
    void smtpWithoutPasswordFailsStartup() {
        runner.withPropertyValues(
                        "carenest.email.provider=smtp",
                        "carenest.email.smtp.host=smtp.gmail.com",
                        "carenest.email.smtp.port=587",
                        "carenest.email.smtp.username=team@example.com")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("CARENEST_EMAIL_SMTP_PASSWORD");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailProperties.class)
    @Import({LoggingEmailGateway.class, SmtpEmailGateway.class})
    static class EmailConfig {}
}
