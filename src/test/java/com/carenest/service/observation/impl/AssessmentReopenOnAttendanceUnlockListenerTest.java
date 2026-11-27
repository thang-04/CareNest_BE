package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.entity.observation.AssessmentDayUnlock;
import com.carenest.repository.observation.AssessmentDayUnlockRepository;
import com.carenest.service.attendance.AttendanceDayUnlockService.AttendanceDayUnlockRevokedEvent;
import com.carenest.service.notification.NotificationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssessmentReopenOnAttendanceUnlockListenerTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 20);
    private static final Instant UNTIL = Instant.parse("2026-10-21T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");

    private final ObservationDayRules rules = mock(ObservationDayRules.class);
    private final AssessmentDayUnlockRepository repository = mock(AssessmentDayUnlockRepository.class);
    private final AssessmentReopenOnAttendanceUnlockListener listener =
            new AssessmentReopenOnAttendanceUnlockListener(rules, repository, mock(NotificationService.class));

    @Test
    @DisplayName("ATT-08: kết thúc sớm mở lại điểm danh thì kết thúc luôn lượt đánh giá được mở kèm")
    void revokeEndsLinkedAssessmentUnlock() {
        AssessmentDayUnlock linked = unlock("Mở cùng điểm danh: GV quên lưu", UNTIL);
        when(rules.activeUnlock(1L, DATE)).thenReturn(Optional.of(linked));
        when(rules.now()).thenReturn(NOW);

        listener.onAttendanceDayUnlockRevoked(new AttendanceDayUnlockRevokedEvent(1L, DATE, UNTIL, 9L));

        assertThat(linked.getValidUntil()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Lượt đánh giá do ban giám hiệu mở riêng không bị kết thúc theo điểm danh")
    void revokeKeepsSeparateAssessmentUnlock() {
        AssessmentDayUnlock separate = unlock("PHT mở riêng", UNTIL);
        when(rules.activeUnlock(1L, DATE)).thenReturn(Optional.of(separate));
        when(rules.now()).thenReturn(NOW);

        listener.onAttendanceDayUnlockRevoked(new AttendanceDayUnlockRevokedEvent(1L, DATE, UNTIL, 9L));

        assertThat(separate.getValidUntil()).isEqualTo(UNTIL);
        verify(repository, never()).save(any());
    }

    private static AssessmentDayUnlock unlock(String reason, Instant until) {
        return AssessmentDayUnlock.create(1L, 2L, DATE, until, reason, 9L, NOW.minusSeconds(60));
    }
}
