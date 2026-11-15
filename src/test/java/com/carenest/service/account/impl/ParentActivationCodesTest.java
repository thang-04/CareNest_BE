package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carenest.entity.account.ParentActivationCode;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.ParentActivationCodeRepository;
import com.carenest.utils.ApiCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ParentActivationCodesTest {
    private static final Long USER = 7L;
    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");

    private final ParentActivationCodeRepository repository = mock(ParentActivationCodeRepository.class);
    private final TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));

    private ParentActivationCodes codesAt(Instant now) {
        return new ParentActivationCodes(repository, tx, Clock.fixed(now, ZoneOffset.UTC));
    }

    private ParentActivationCode rowFor(String code) {
        var row = ParentActivationCode.issue(
                USER, ParentActivationCodes.hash(USER, code), NOW, NOW.plus(ParentActivationCodes.TTL));
        given(repository.findForUpdate(USER)).willReturn(Optional.of(row));
        return row;
    }

    @Test
    @DisplayName("AUTH-18: mã 6 chữ số")
    void generatesSixDigits() {
        assertThat(codesAt(NOW).generate()).matches("\\d{6}");
    }

    @Test
    @DisplayName("AUTH-18: mã đúng trong hạn ⇒ hợp lệ")
    void correctCodeMatches() {
        rowFor("123456");
        assertThat(codesAt(NOW.plusSeconds(10)).verify(USER, "123456")).isTrue();
    }

    @Test
    @DisplayName("AUTH-18: sai 5 lần ⇒ mã hủy, mã đúng sau đó cũng bị từ chối")
    void fiveFailuresLockCode() {
        var row = rowFor("123456");
        var codes = codesAt(NOW.plusSeconds(10));
        for (int i = 0; i < 5; i++) assertThat(codes.verify(USER, "000000")).isFalse();

        assertThat(row.getFailedAttempts()).isEqualTo((short) 5);
        assertThat(codes.verify(USER, "123456")).isFalse();
    }

    @Test
    @DisplayName("AUTH-18: quá 72 giờ ⇒ từ chối")
    void expiredCodeRejected() {
        rowFor("123456");
        assertThat(codesAt(NOW.plus(Duration.ofHours(72))).verify(USER, "123456"))
                .isFalse();
    }

    @Test
    @DisplayName("AUTH-18: đã kích hoạt ⇒ mã không dùng lại được")
    void usedCodeRejected() {
        rowFor("123456").markActivated(NOW);
        assertThat(codesAt(NOW.plusSeconds(10)).verify(USER, "123456")).isFalse();
    }

    @Test
    @DisplayName("AUTH-18: gửi lại trong 60 giây ⇒ từ chối")
    void resendTooSoonRejected() {
        rowFor("123456");
        assertThatThrownBy(() -> codesAt(NOW.plusSeconds(59)).reissue(USER))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.OTP_RESEND_TOO_SOON);
    }

    @Test
    @DisplayName("AUTH-18: gửi lại sau 60 giây ⇒ mã cũ hết hiệu lực, mã mới dùng được")
    void resendReplacesOldCode() {
        rowFor("123456");
        var codes = codesAt(NOW.plusSeconds(61));

        var issued = codes.reissue(USER);

        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(61).plus(ParentActivationCodes.TTL));
        assertThat(codes.verify(USER, issued.code())).isTrue();
        if (!issued.code().equals("123456"))
            assertThat(codes.verify(USER, "123456")).isFalse();
    }

    @Test
    @DisplayName("AUTH-18: tài khoản không chờ kích hoạt ⇒ không gửi lại")
    void resendRequiresPendingActivation() {
        given(repository.findForUpdate(USER)).willReturn(Optional.empty());
        assertThatThrownBy(() -> codesAt(NOW).reissue(USER))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.ACTIVATION_NOT_PENDING);
    }

    @Test
    @DisplayName("AUTH-18: cùng mã ở hai tài khoản cho hash khác nhau, không lưu mã gốc")
    void hashBindsUser() {
        assertThat(ParentActivationCodes.hash(1L, "123456"))
                .isNotEqualTo(ParentActivationCodes.hash(2L, "123456"))
                .doesNotContain("123456")
                .hasSize(64);
    }
}
