package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** EDU-97: ngày khóa là mốc hạn cộng N ngày lịch, không lẫn với hạn gửi lại. */
class EduReviewDeadlineTest {
    @Test
    void allowsConfiguredDaysThenLocksOnLockFrom() {
        LocalDate submitted = LocalDate.of(2026, 12, 10);

        assertThat(EduReviewDeadline.of(submitted, 2, null, LocalDate.of(2026, 12, 11))
                        .locked())
                .isFalse();
        EduReviewDeadline.Info locked = EduReviewDeadline.of(submitted, 2, null, LocalDate.of(2026, 12, 12));
        assertThat(locked.lockFrom()).isEqualTo(LocalDate.of(2026, 12, 12));
        assertThat(locked.locked()).isTrue();
    }

    @Test
    void principalReopenUsesManualDeadlineWithoutAddingGraceAgain() {
        EduReviewDeadline.Info open = EduReviewDeadline.of(
                LocalDate.of(2026, 12, 10), 2, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 20));
        assertThat(open.locked()).isFalse();
        assertThat(open.lockFrom()).isEqualTo(LocalDate.of(2026, 12, 21));
        assertThat(EduReviewDeadline.of(
                                LocalDate.of(2026, 12, 10), 2, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 21))
                        .locked())
                .isTrue();
    }

    @Test
    void supportsBoundaryConfigurationWithoutChangingTheExclusiveLockRule() {
        LocalDate cutoff = LocalDate.of(2026, 12, 10);

        assertThat(EduReviewDeadline.of(cutoff, 0, null, cutoff).locked()).isTrue();
        assertThat(EduReviewDeadline.of(cutoff, 30, null, cutoff.plusDays(29)).locked())
                .isFalse();
        assertThat(EduReviewDeadline.of(cutoff, 30, null, cutoff.plusDays(30)).locked())
                .isTrue();
    }
}
