package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** EDU-109: tới hạn khóa ngay, không ngày thêm; HT mở hạn nhiều lần; ban hành sau hạn không có nhãn hạn. */
class EduDeadlineGraceTest {
    private static final LocalDate DEADLINE = LocalDate.of(2026, 11, 30);

    @Test
    void overdueFromDeadlineDayWithoutGrace() {
        var info = EduDeadline.of(DEADLINE, null, DEADLINE, false, null, null);
        assertThat(info.state()).isEqualTo(EduDeadline.OVERDUE);
        assertThat(info.effective()).isEqualTo(DEADLINE);
        assertThat(EduDeadline.of(DEADLINE, null, DEADLINE.minusDays(1), false, null, null).state())
                .isEqualTo(EduDeadline.NEAR);
    }

    @Test
    void repeatedExtensionUsesLatestDateAndLocksAgain() {
        LocalDate second = DEADLINE.plusDays(10);
        assertThat(EduDeadline.of(DEADLINE, second, DEADLINE.plusDays(8), false, null, null).state())
                .isEqualTo(EduDeadline.NEAR);
        assertThat(EduDeadline.of(DEADLINE, second, second, false, null, null).state())
                .isEqualTo(EduDeadline.OVERDUE);
    }

    @Test
    void releaseAfterDeadlineHasNoLateLabel() {
        assertThat(EduDeadline.of(DEADLINE, null, DEADLINE.plusDays(3), true, DEADLINE.plusDays(1), null).state())
                .isEqualTo(EduDeadline.ISSUED);
    }

    @Test
    void reviewLocksOnDeadlineDayAndReopenCanRepeat() {
        assertThat(EduReviewDeadline.of(DEADLINE, null, DEADLINE.minusDays(1)).locked()).isFalse();
        assertThat(EduReviewDeadline.of(DEADLINE, null, DEADLINE).locked()).isTrue();
        LocalDate reopen = DEADLINE.plusDays(3);
        assertThat(EduReviewDeadline.of(DEADLINE, reopen, reopen).locked()).isFalse();
        assertThat(EduReviewDeadline.of(DEADLINE, reopen, reopen.plusDays(1)).locked()).isTrue();
    }
}
