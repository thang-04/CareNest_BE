package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Trả lại sau hạn (Tiền 18/11/2026): N ngày lịch tính từ lần trả lại trễ đầu tiên, một lần mỗi tài liệu. */
class EduDeadlineGraceTest {
    private static final LocalDate DEADLINE = LocalDate.of(2026, 11, 30);

    private static EduDeadline.Event reject(LocalDate d) {
        return new EduDeadline.Event(d, true, null);
    }

    @Test
    void returnBeforeDeadlineKeepsOriginalDeadline() {
        assertThat(EduDeadline.graceTo(DEADLINE, List.of(reject(DEADLINE.minusDays(1))), 2))
                .isNull();
    }

    @Test
    void firstLateReturnGivesNCalendarDaysAfterReturnDay() {
        // Trả lại Thứ 6 04/12, N = 2 ⇒ gửi được tới hết Chủ nhật 06/12, Thứ 2 07/12 thành quá hạn
        LocalDate friday = LocalDate.of(2026, 12, 4);
        assertThat(EduDeadline.graceTo(DEADLINE, List.of(reject(friday)), 2)).isEqualTo(LocalDate.of(2026, 12, 7));
    }

    @Test
    void laterReturnsDoNotExtendAgainEvenAfterPrincipalExtension() {
        LocalDate first = DEADLINE.plusDays(1);
        var events = List.of(
                reject(first),
                new EduDeadline.Event(null, false, DEADLINE.plusDays(10)),
                reject(DEADLINE.plusDays(12)));
        assertThat(EduDeadline.graceTo(DEADLINE, events, 2)).isEqualTo(first.plusDays(3));
    }

    @Test
    void returnInsidePrincipalExtensionIsNotLate() {
        var events = List.of(new EduDeadline.Event(null, false, DEADLINE.plusDays(10)), reject(DEADLINE.plusDays(5)));
        assertThat(EduDeadline.graceTo(DEADLINE, events, 2)).isNull();
    }

    @Test
    void graceUnlocksSendingButKeepsLateReleaseLabel() {
        LocalDate today = DEADLINE.plusDays(2);
        LocalDate grace = DEADLINE.plusDays(4);
        var open = EduDeadline.of(DEADLINE, null, grace, today, false, null, null);
        assertThat(open.state()).isNotEqualTo(EduDeadline.OVERDUE);
        assertThat(EduDeadline.of(DEADLINE, null, grace, grace, false, null, null)
                        .state())
                .isEqualTo(EduDeadline.OVERDUE);
        assertThat(EduDeadline.of(DEADLINE, null, grace, today, true, today, null)
                        .state())
                .isEqualTo(EduDeadline.LATE);
    }
}
