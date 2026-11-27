package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.service.organization.AttendanceCutoffService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PCK-04: mốc tính đủ ngày dùng chung cho đón trẻ và lưu lại điểm danh. */
class EarlyPickupRuleTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    private final AttendanceCutoffService cutoffService = mock(AttendanceCutoffService.class);
    private final EarlyPickupRule rule = new EarlyPickupRule(cutoffService);

    @Test
    void defaultThresholdIsThreePm() {
        when(cutoffService.findFullDayPickupFrom(1L, DAY)).thenReturn(Optional.empty());

        assertThat(rule.beforeFullDay(1L, DAY, LocalTime.of(14, 45))).isTrue();
        assertThat(rule.beforeFullDay(1L, DAY, LocalTime.of(15, 0))).isFalse();
    }

    @Test
    void configuredThresholdOfTheDayIsUsed() {
        when(cutoffService.findFullDayPickupFrom(1L, DAY)).thenReturn(Optional.of(LocalTime.of(16, 0)));

        assertThat(rule.beforeFullDayOn(1L, DAY).test(LocalTime.of(15, 59))).isTrue();
        assertThat(rule.beforeFullDayOn(1L, DAY).test(LocalTime.of(16, 0))).isFalse();
    }
}
