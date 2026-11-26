package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.attendance.AttendanceRecord;
import com.carenest.repository.attendance.AttendanceRecordHistoryRepository;
import com.carenest.repository.attendance.AttendanceRecordRepository;
import com.carenest.repository.attendance.DailyHealthEntryRepository;
import com.carenest.repository.attendance.LeaveRequestRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.ChildClassHistoryService;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Đã đến buổi chiều và khóa nghỉ sáng (ATT-13): báo sổ suất ăn, khóa lớp rồi mới xét lại trạng thái. */
class AttendanceServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
    private static final long CLASS = 10L;
    private static final long CAMPUS = 1L;
    private static final long CHILD = 100L;

    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final AttendanceDayRules rules = mock(AttendanceDayRules.class);
    private final ClassroomService classrooms = mock(ClassroomService.class);
    private final EnrollmentService enrollments = mock(EnrollmentService.class);
    private final ChildService children = mock(ChildService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final GuardianChildLinkService guardians = mock(GuardianChildLinkService.class);
    private AttendanceServiceImpl service;
    private AttendanceRecord morningLeave;

    @BeforeEach
    void setUp() {
        service = new AttendanceServiceImpl(
                records,
                mock(AttendanceRecordHistoryRepository.class),
                mock(DailyHealthEntryRepository.class),
                rules,
                classrooms,
                mock(ChildClassHistoryService.class),
                enrollments,
                children,
                mock(AccountService.class),
                events,
                mock(LeaveRequestRepository.class),
                guardians,
                mock(NotificationService.class),
                mock(com.carenest.repository.attendance.PickupRecordRepository.class),
                new EarlyPickupRule(mock(com.carenest.service.organization.AttendanceCutoffService.class)));
        when(classrooms.findClassroomInfo(CLASS))
                .thenReturn(Optional.of(new ClassroomInfoResponse(
                        CLASS,
                        "Lớp A",
                        CAMPUS,
                        7L,
                        SchoolYearStatus.ACTIVE,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2027, 5, 31),
                        3L,
                        List.of(MealSession.LUNCH, MealSession.AFTERNOON),
                        null,
                        null)));
        when(rules.today()).thenReturn(TODAY);
        when(rules.now()).thenReturn(NOW);
        when(rules.isSchoolDay(TODAY)).thenReturn(true);
        when(rules.latestArrival(7L, TODAY)).thenReturn(LocalTime.of(14, 0));
        morningLeave = AttendanceRecord.create(
                CHILD, CLASS, CAMPUS, TODAY, AttendanceStatus.EXCUSED, List.of(MealSession.AFTERNOON), 5L, NOW);
        morningLeave.markAbsentPart(DayPart.MORNING);
    }

    @Test
    void markArrivedNotifiesMealCountLikeSavingAttendance() {
        CurrentUser teacher = new CurrentUser(1L, "kc", "t", "t", null, Set.of(CareNestRole.TEACHER), Set.of());
        when(rules.canRecordAttendanceDay(
                        teacher, classrooms.findClassroomInfo(CLASS).orElseThrow(), TODAY))
                .thenReturn(true);
        when(rules.dayInfo(eq(TODAY), any(), any()))
                .thenReturn(
                        new DayInfoResponse(TODAY, TODAY, "13:00", "08:30", null, null, true, true, NOW, true, null));
        when(rules.isBaseLocked(TODAY, LocalTime.of(14, 0))).thenReturn(false);
        when(enrollments.findChildrenInClassroom(CLASS, TODAY))
                .thenReturn(List.of(new ClassChildResponse(CHILD, "An", LocalDate.of(2023, 1, 1))));
        when(records.findByChildIdInAndDate(List.of(CHILD), TODAY)).thenReturn(List.of(morningLeave));

        service.markArrived(teacher, CLASS, CHILD, TODAY);

        verify(events).publishEvent(new AttendanceService.AttendanceChangedEvent(TODAY, CAMPUS, CLASS));
    }

    @Test
    void closingMorningLeavesRechecksStatusAfterLockingClass() {
        when(records.findByDateAndAbsentPartAndStatus(TODAY, DayPart.MORNING, AttendanceStatus.EXCUSED))
                .thenReturn(List.of(morningLeave));
        when(records.findClassroomIdsWithOpenMorningLeave(TODAY)).thenReturn(List.of(CLASS));
        when(records.findByClassroomIdInAndDate(List.of(CLASS), TODAY)).thenReturn(List.of(morningLeave));
        when(rules.isLockedAt(eq(TODAY), any(), any())).thenReturn(true);
        when(children.findChildBriefs(anyList())).thenReturn(Map.of());
        // Trẻ được bấm "Đã đến" trong lúc job chờ khóa lớp
        doAnswer(inv -> {
                    morningLeave.update(AttendanceStatus.PRESENT, morningLeave.getMeals(), 1L, NOW);
                    return null;
                })
                .when(classrooms)
                .lockForUpdate(CLASS);

        int closed = service.closeMorningLeaves();

        assertThat(closed).isZero();
        assertThat(morningLeave.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }
}
