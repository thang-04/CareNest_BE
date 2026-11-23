package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** EDU-109 (9): khóa duyệt = đúng trạng thái Quá hạn của hạn hiệu lực chung. */
class EduReviewDeadlineTest {
    private static final LocalDate HAN = LocalDate.of(2026, 12, 10);

    private static EduReviewDeadline.Info at(LocalDate extended, LocalDate today) {
        return EduReviewDeadline.of(EduDeadline.of(HAN, extended, today, false, null));
    }

    @Test
    void locksOnDeadlineDay() {
        assertThat(at(null, HAN.minusDays(1)).locked()).isFalse();
        assertThat(at(null, HAN).locked()).isTrue();
        assertThat(at(null, HAN).lockFrom()).isEqualTo(HAN);
    }

    @Test
    void reopenMovesSharedDeadlineThenLocksAgain() {
        LocalDate reopen = HAN.plusDays(10);
        assertThat(at(reopen, reopen.minusDays(1)).locked()).isFalse();
        assertThat(at(reopen, reopen).locked()).isTrue();
        assertThat(at(reopen, reopen).lockFrom()).isEqualTo(reopen);
    }

    @Test
    void noDeadlineOrIssuedIsNotLocked() {
        assertThat(EduReviewDeadline.of(EduDeadline.of(null, null, HAN, false, null)).locked()).isFalse();
        assertThat(EduReviewDeadline.of(EduDeadline.of(HAN, null, HAN.plusDays(1), true, HAN)).locked()).isFalse();
    }
}
