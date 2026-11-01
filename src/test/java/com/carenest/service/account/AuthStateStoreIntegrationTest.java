package com.carenest.service.account;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.carenest.entity.account.*;
import com.carenest.repository.account.*;
import com.carenest.utils.enums.PasswordTokenPurpose;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AuthStateStoreIntegrationTest {

    @Test
    void expiredAuthStateIsPrunedWithoutRemovingLiveRecoveryMetadata() {
        otp();
        store.cleanup(now.plusSeconds(2000));
        assertThat(passwordTokens.findById("b".repeat(64))).isPresent();
        store.cleanup(now.plusSeconds(4000));
        assertThat(passwordTokens.findById("b".repeat(64))).isEmpty();
    }

    @Test
    void forgotAndResendShareTheSameFiveSendBudget() {
        otp();
        String rate = "f".repeat(64);
        assertThat(store.takeRate(rate, 5, now.plusSeconds(1800))).isTrue();
        for (int i = 1; i <= 4; i++) {
            assertThat(store.resend(
                                    "b".repeat(64),
                                    "digest-" + i,
                                    now.plusSeconds(60L * i),
                                    rate,
                                    now.plusSeconds(1800))
                            .data()
                            .userId())
                    .isEqualTo(account.getId());
        }
        var suppressed =
                store.resend("b".repeat(64), "digest-extra", now.plusSeconds(300), rate, now.plusSeconds(1800));
        assertThat(suppressed.status()).isEqualTo(AuthStateStore.Status.READY);
        assertThat(suppressed.data().userId()).isNull();
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    AuthStateStore store;

    @Autowired
    UserAccountRepository users;

    @Autowired
    PasswordTokenRepository passwordTokens;

    private final Instant now = Instant.parse("2026-10-12T00:00:00Z");
    private UserAccount account;

    @BeforeEach
    void prepare() {
        account = users.saveAndFlush(UserAccount.create("kc-state-test", "state-test", "state@example.test", "Demo"));
    }

    @AfterEach
    void clear() {
        users.deleteAll();
        passwordTokens.deleteAll();
    }

    private void otp() {
        store.savePasswordToken(PasswordToken.create(
                "b".repeat(64),
                PasswordTokenPurpose.OTP,
                account.getId(),
                account.getKeycloakUserId(),
                "c".repeat(64),
                null,
                "d".repeat(64),
                now,
                now.plusSeconds(300),
                now.plusSeconds(60)));
    }

    @Test
    void wrongOtpCountersCommitAndStopAfterFiveAttempts() {
        assertDoesNotThrow(this::otp);
        for (int i = 0; i < 5; i++) store.verifyOtp("b".repeat(64), "wrong", now);
        assertThat(passwordTokens.findById("b".repeat(64)).orElseThrow().getAttempts())
                .isEqualTo(5);
        assertThat(store.verifyOtp("b".repeat(64), "d".repeat(64), now).status())
                .isEqualTo(AuthStateStore.Status.THROTTLED);
    }

    @Test
    void otpCanAuthorizeOnlyOnePasswordRecoveryAndResendReplacesOldCode() {
        assertDoesNotThrow(this::otp);
        assertThat(store.resend("b".repeat(64), "new-code", now).status()).isEqualTo(AuthStateStore.Status.THROTTLED);
        assertThat(store.resend("b".repeat(64), "new-code", now.plusSeconds(60)).status())
                .isEqualTo(AuthStateStore.Status.READY);
        assertThat(store.verifyOtp("b".repeat(64), "d".repeat(64), now.plusSeconds(60))
                        .status())
                .isEqualTo(AuthStateStore.Status.INVALID);
        assertThat(store.verifyOtp("b".repeat(64), "new-code", now.plusSeconds(60))
                        .status())
                .isEqualTo(AuthStateStore.Status.READY);
        assertThat(store.verifyOtp("b".repeat(64), "new-code", now.plusSeconds(60))
                        .status())
                .isEqualTo(AuthStateStore.Status.INVALID);
    }

    @Test
    void passwordChallengeIsFencedAndRejectsOtpPurpose() {
        assertDoesNotThrow(this::otp);
        assertThat(store.claimPassword("b".repeat(64), "owner", now).status()).isEqualTo(AuthStateStore.Status.INVALID);
    }

    @Test
    void resendDoesNotResetWrongAttemptBudget() {
        otp();
        for (int i = 0; i < 4; i++) store.verifyOtp("b".repeat(64), "wrong", now);
        store.resend("b".repeat(64), "new-code", now.plusSeconds(60));
        assertThat(passwordTokens.findById("b".repeat(64)).orElseThrow().getAttempts())
                .isEqualTo(4);
        assertThat(store.verifyOtp("b".repeat(64), "wrong", now.plusSeconds(61)).status())
                .isEqualTo(AuthStateStore.Status.THROTTLED);
    }
}
