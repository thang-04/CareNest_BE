package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.MealCount;
import com.carenest.repository.meals.MealCountHistoryRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.AttendanceService.ClassMealTally;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Điểm danh đổi: sổ báo ăn chỉ cập nhật sổ của năm học của lớp, kể cả khi điểm trường có sổ của năm khác cùng ngày. */
class MealCountAttendanceYearTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);
    private static final long CAMPUS = 1L;
    private static final long CLASS = 10L;
    private static final long CLASS_YEAR = 7L;
    private static final long OTHER_YEAR = 99L;

    @Test
    void attendanceChangeUpdatesOnlyTheCountOfTheClassYear() {
        MealCountRepository counts = mock(MealCountRepository.class);
        AttendanceService attendance = mock(AttendanceService.class);
        ClassroomService classrooms = mock(ClassroomService.class);
        Instant at = Instant.parse("2026-10-21T01:00:00Z");
        MealCount other = MealCount.aggregate(CAMPUS, OTHER_YEAR, DAY, MealSession.LUNCH, at);
        other.addLine(CLASS, 0, 0, 0, 0);
        MealCount own = MealCount.aggregate(CAMPUS, CLASS_YEAR, DAY, MealSession.LUNCH, at);
        own.addLine(CLASS, 0, 0, 0, 0);
        when(counts.findByCampusIdAndDate(CAMPUS, DAY)).thenReturn(List.of(other, own));
        when(classrooms.findClassroomInfo(CLASS))
                .thenReturn(Optional.of(new ClassroomInfoResponse(
                        CLASS,
                        "Lớp A",
                        CAMPUS,
                        CLASS_YEAR,
                        SchoolYearStatus.ACTIVE,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2027, 5, 31),
                        3L,
                        List.of(MealSession.LUNCH),
                        null,
                        null)));
        when(attendance.tallyMeals(DAY, CAMPUS, CLASS_YEAR))
                .thenReturn(List.of(new ClassMealTally(
                        CLASS, "Lớp A", CAMPUS, List.of(MealSession.LUNCH), 1, 0, 0, Map.of(MealSession.LUNCH, 1))));
        MealCountServiceImpl service = new MealCountServiceImpl(
                counts,
                mock(MealCountHistoryRepository.class),
                attendance,
                classrooms,
                mock(StaffAssignmentService.class),
                mock(AccountService.class),
                mock(CampusAccessPolicy.class),
                mock(ApplicationEventPublisher.class),
                mock(NotificationService.class),
                mock(BoardingWriteGuard.class),
                Clock.fixed(at, ZoneId.of("Asia/Ho_Chi_Minh")));

        service.markAttendanceChanged(DAY, CAMPUS, CLASS);

        assertThat(own.visibleLine(CLASS).orElseThrow().getPresent()).isEqualTo(1);
        assertThat(other.visibleLine(CLASS).orElseThrow().getPresent()).isZero();
        verify(attendance, never()).tallyMeals(any(), anyLong(), eq(OTHER_YEAR));
    }
}
