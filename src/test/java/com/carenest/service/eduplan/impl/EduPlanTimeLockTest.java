package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduPlanEvent;
import com.carenest.repository.eduplan.EduPlanEventRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.service.organization.SchoolStructureService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 004 (Q3b, Q5): kế hoạch năm/tổ khóa từ ngày bắt đầu năm học; Hiệu trưởng mở khóa ⇒ làm được tới hạn mới. */
class EduPlanTimeLockTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final SchoolStructureService years = mock(SchoolStructureService.class);
    private final EduPlanEventRepository events = mock(EduPlanEventRepository.class);
    private final EduPlanRepository plans = mock(EduPlanRepository.class);
    private final EduPlan plan = EduPlan.createDraft("KH", 1L, 2L, "Tổ", "", 7L, "TT", clock.instant());

    private final EduPlan issued = mock(EduPlan.class);

    {
        when(issued.getId()).thenReturn(9L);
        when(plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(any(), any(), any()))
                .thenReturn(List.of(issued));
    }

    private void yearStarts(LocalDate start) {
        when(years.findSchoolYear(any())).thenReturn(Optional.of(new SchoolYearResponse(1L, "2026-2027", start, start.plusMonths(9))));
    }

    @Test
    void locksFromSchoolYearStartUntilPrincipalUnlocks() {
        when(events.findAllByYearPlanIdOrderByOccurredAtAscIdAsc(any())).thenReturn(List.of());
        yearStarts(LocalDate.of(2026, 11, 1));
        assertThat(new EduPlanTimeLock(years, events, plans, clock, true).locked(plan)).isFalse();

        yearStarts(LocalDate.of(2026, 9, 1));
        EduPlanTimeLock lock = new EduPlanTimeLock(years, events, plans, clock, true);
        assertThat(lock.locked(plan)).isTrue();
        assertThat(lock.info(plan).state()).isEqualTo(EduDeadline.OVERDUE);

        EduPlanEvent unlock = EduPlanEvent.of(null, EduPlanTimeLock.ACTION_UNLOCK, "Mở khóa", 1L, "HT", "Hiệu trưởng", "",
                        "Lý do", clock.instant())
                .withDeadline(LocalDate.of(2026, 10, 30));
        when(events.findAllByYearPlanIdOrderByOccurredAtAscIdAsc(any())).thenReturn(List.of(unlock));
        assertThat(lock.locked(plan)).isFalse();
        assertThat(lock.info(plan).extendedTo()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void unlockOnIssuedVersionAlsoOpensLaterAdjustmentVersion() {
        yearStarts(LocalDate.of(2026, 9, 1));
        EduPlanEvent unlock = EduPlanEvent.of(null, EduPlanTimeLock.ACTION_UNLOCK, "Mở khóa", 1L, "HT", "Hiệu trưởng", "",
                        "Lý do", clock.instant())
                .withDeadline(LocalDate.of(2026, 10, 30));
        when(events.findAllByYearPlanIdOrderByOccurredAtAscIdAsc(any())).thenReturn(List.of());
        when(events.findAllByYearPlanIdOrderByOccurredAtAscIdAsc(eq(9L))).thenReturn(List.of(unlock));
        // plan (phiên bản điều chỉnh, chưa có sự kiện riêng) cùng chuỗi với bản ban hành id 9 đã được mở khóa
        assertThat(new EduPlanTimeLock(years, events, plans, clock, true).locked(plan)).isFalse();
    }

    @Test
    void disabledFlagNeverLocks() {
        when(events.findAllByYearPlanIdOrderByOccurredAtAscIdAsc(any())).thenReturn(List.of());
        yearStarts(LocalDate.of(2026, 9, 1));
        assertThat(new EduPlanTimeLock(years, events, plans, clock, false).locked(plan)).isFalse();
    }
}
