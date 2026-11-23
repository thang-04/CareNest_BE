package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** EDU-109: khóa duyệt từ đúng ngày hạn, không ngày duyệt thêm; HT mở lại thì duyệt hết ngày hạn mới. */
class EduReviewDeadlineTest {
    @Test
    void locksOnDeadlineDay() {
        LocalDate cutoff = LocalDate.of(2026, 12, 10);

        assertThat(EduReviewDeadline.of(cutoff, null, cutoff.minusDays(1)).locked()).isFalse();
        EduReviewDeadline.Info locked = EduReviewDeadline.of(cutoff, null, cutoff);
        assertThat(locked.lockFrom()).isEqualTo(cutoff);
        assertThat(locked.locked()).isTrue();
    }

    @Test
    void principalReopenUsesManualDeadline() {
        LocalDate cutoff = LocalDate.of(2026, 12, 10);
        LocalDate reopen = LocalDate.of(2026, 12, 20);
        EduReviewDeadline.Info open = EduReviewDeadline.of(cutoff, reopen, reopen);
        assertThat(open.locked()).isFalse();
        assertThat(open.lockFrom()).isEqualTo(reopen.plusDays(1));
        assertThat(EduReviewDeadline.of(cutoff, reopen, reopen.plusDays(1)).locked()).isTrue();
    }

    @Test
    void submissionAfterCutoffHasNoReviewLock() {
        LocalDate cutoff = LocalDate.of(2026, 12, 10);
        assertThat(EduReviewDeadline.baseOf(cutoff.minusDays(2), cutoff)).isEqualTo(cutoff);
        assertThat(EduReviewDeadline.baseOf(cutoff.plusDays(1), cutoff)).isNull();
        assertThat(EduReviewDeadline.baseOf(cutoff, null)).isNull();
    }

    @Test
    void noSubmissionMeansNotLocked() {
        assertThat(EduReviewDeadline.of(null, null, LocalDate.of(2026, 12, 10)).locked()).isFalse();
    }
}
