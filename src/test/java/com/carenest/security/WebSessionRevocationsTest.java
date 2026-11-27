package com.carenest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carenest.entity.account.RevokedWebSession;
import com.carenest.repository.account.RevokedWebSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class WebSessionRevocationsTest {
    private final RevokedWebSessionRepository repository = mock(RevokedWebSessionRepository.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T03:00:00Z"));
    private final WebSessionRevocations revocations = new WebSessionRevocations(repository, clock);

    @Test
    @DisplayName("AUTH-28: token còn hạn của phiên bị thay thế bị từ chối ngay, phiên khác vẫn qua")
    void rejectsTokensOfReplacedSessionOnly() {
        revocations.revoke("kc-user", List.of("old-sid"));

        assertThat(revocations.validate(jwt("old-sid")).hasErrors()).isTrue();
        assertThat(revocations.validate(jwt("old-sid")).getErrors())
                .anyMatch(error -> WebSessionRevocations.ERROR_CODE.equals(error.getErrorCode()));
        assertThat(revocations.validate(jwt("new-sid")).hasErrors()).isFalse();
        assertThat(revocations.validate(jwt(null)).hasErrors()).isFalse();
        verify(repository).save(any(RevokedWebSession.class));
    }

    @Test
    @DisplayName("AUTH-28: hết thời gian giữ (sau khi token cũ chắc chắn hết hạn) thì dọn khỏi bộ nhớ và DB")
    void forgetsRevocationAfterHoldAndPurges() {
        revocations.revoke("kc-user", List.of("old-sid"));
        clock.now = clock.now.plusSeconds(601);

        assertThat(revocations.isRevoked("old-sid")).isFalse();
        revocations.purgeExpired();
        verify(repository).deleteExpired(clock.now);
    }

    @Test
    @DisplayName("AUTH-28: khởi động lại API thì nạp lại phiên bị thu hồi còn hiệu lực từ DB")
    void reloadsActiveRevocationsOnStartup() {
        given(repository.findByExpiresAtAfter(clock.now))
                .willReturn(List.of(
                        new RevokedWebSession("old-sid", "kc-user", clock.now, clock.now.plusSeconds(300), "REPLACED"),
                        new RevokedWebSession("ended-sid", "kc-user", clock.now, clock.now.plusSeconds(300), "ENDED")));
        revocations.loadActive();
        assertThat(revocations.isRevoked("old-sid")).isTrue();
        assertThat(revocations.validate(jwt("old-sid")).getErrors())
                .anyMatch(error -> WebSessionRevocations.ERROR_CODE.equals(error.getErrorCode()));
        assertThat(revocations.validate(jwt("ended-sid")).getErrors())
                .anyMatch(error -> WebSessionRevocations.ENDED_ERROR_CODE.equals(error.getErrorCode()));
    }

    @Test
    @DisplayName("Đăng xuất: token còn hạn của đúng phiên đó bị từ chối với lý do ENDED (không phải bị thay thế)")
    void endedSessionIsRejectedAsSignedOut() {
        revocations.end("kc-user", "ended-sid");

        assertThat(revocations.validate(jwt("ended-sid")).getErrors())
                .anyMatch(error -> WebSessionRevocations.ENDED_ERROR_CODE.equals(error.getErrorCode()))
                .noneMatch(error -> WebSessionRevocations.ERROR_CODE.equals(error.getErrorCode()));
        assertThat(revocations.validate(jwt("other-sid")).hasErrors()).isFalse();
        verify(repository)
                .save(org.mockito.ArgumentMatchers.argThat(
                        row -> "ENDED".equals(row.getReason()) && "ended-sid".equals(row.getSessionId())));
    }

    private Jwt jwt(String sid) {
        var builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("kc-user");
        if (sid != null) builder.claim("sid", sid);
        return builder.build();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
