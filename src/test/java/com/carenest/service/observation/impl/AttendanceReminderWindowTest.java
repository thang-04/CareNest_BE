package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.repository.observation.AssessmentDayUnlockRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.repository.observation.EvaluationUnlockRepository;
import com.carenest.repository.observation.WeeklyCommentUnlockRepository;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.observation.EvaluationDeadlineService;
import com.carenest.service.observation.WeeklyCommentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AttendanceReminderWindowTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 17);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private ObservationDayRules rules;
    private AttendanceService attendance;
    private ClassroomService classrooms;
    private StaffAssignmentService staff;
    private NotificationService notifications;
    private Map<String, Object> timingOverrides = Map.of();

    @BeforeEach
    void setup() {
        rules = mock(ObservationDayRules.class);
        attendance = mock(AttendanceService.class);
        classrooms = mock(ClassroomService.class);
        staff = mock(StaffAssignmentService.class);
        notifications = mock(NotificationService.class);
        given(rules.today()).willReturn(DAY);
        ClassroomInfoResponse classroom = mock(ClassroomInfoResponse.class);
        given(classroom.id()).willReturn(10L);
        given(classroom.name()).willReturn("Lớp mẫu");
        given(classroom.yearStatus()).willReturn(SchoolYearStatus.ACTIVE);
        given(classrooms.findActiveYearClassrooms()).willReturn(List.of(classroom));
        given(staff.findCurrentMainTeacherIds(anyCollection(), eq(DAY))).willReturn(Map.of(10L, List.of(50L)));
        given(staff.findSubstituteTeacherIdsOn(anyCollection(), eq(DAY))).willReturn(Map.of(10L, List.of(51L)));
    }

    private AssessmentReminderServiceImpl at(String time, String cutoff, boolean schoolDay, boolean locked) {
        Clock clock = Clock.fixed(DAY.atTime(LocalTime.parse(time)).atZone(VN).toInstant(), VN);
        given(attendance.getActiveYearDayInfo(DAY))
                .willReturn(new DayInfoResponse(
                        DAY,
                        DAY,
                        time,
                        cutoff,
                        DAY.minusDays(1),
                        DAY.plusDays(1),
                        schoolDay,
                        locked,
                        clock.instant(),
                        locked,
                        null));
        return new AssessmentReminderServiceImpl(
                rules,
                classrooms,
                staff,
                attendance,
                mock(DailyObservationRepository.class),
                notifications,
                mock(EvaluationDeadlineService.class),
                mock(WeeklyCommentService.class),
                mock(EvaluationUnlockRepository.class),
                mock(WeeklyCommentUnlockRepository.class),
                mock(AssessmentDayUnlockRepository.class),
                clock,
                com.carenest.support.NotificationTimingTestConfig.load(timingOverrides));
    }

    @Test
    void onlyRemindsFromEightUntilConfiguredCutoffEvenAfterNoon() {
        assertThat(at("07:55", "14:30", true, false).remindAttendanceCutoff()).isZero();
        assertThat(at("08:00", "14:30", true, false).remindAttendanceCutoff()).isEqualTo(2);
        assertThat(at("13:05", "14:30", true, false).remindAttendanceCutoff()).isEqualTo(2);
        assertThat(at("14:30", "14:30", true, false).remindAttendanceCutoff()).isZero();
        assertThat(at("14:35", "14:30", true, false).remindAttendanceCutoff()).isZero();
        assertThat(at("08:00", "07:30", true, false).remindAttendanceCutoff()).isZero();
    }

    @Test
    void holidayLockedOrAlreadyTakenClassDoesNotGetReminder() {
        assertThat(at("08:00", "09:00", false, false).remindAttendanceCutoff()).isZero();
        assertThat(at("08:00", "09:00", true, true).remindAttendanceCutoff()).isZero();
        given(attendance.findClassDays(anyCollection(), eq(DAY), eq(DAY)))
                .willReturn(List.of(new AttendanceService.ClassChildDay(
                        10L, 1L, DAY, com.carenest.utils.enums.AttendanceStatus.PRESENT, List.of())));
        assertThat(at("08:00", "09:00", true, false).remindAttendanceCutoff()).isZero();
        verifyNoInteractions(notifications);
    }

    @Test
    void dedupKeyIsStableWithinSlotAndChangesEveryFiveMinutes() {
        at("08:00", "09:00", true, false).remindAttendanceCutoff();
        at("08:03", "09:00", true, false).remindAttendanceCutoff();
        at("08:05", "09:00", true, false).remindAttendanceCutoff();
        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(notifications, times(3))
                .notifyUsers(
                        eq(List.of(50L, 51L)),
                        eq(NotificationType.ATTENDANCE_REMINDER),
                        anyString(),
                        anyString(),
                        links.capture(),
                        eq(true));
        assertThat(links.getAllValues().get(0)).isEqualTo(links.getAllValues().get(1));
        assertThat(links.getAllValues().get(2))
                .isNotEqualTo(links.getAllValues().get(0));
    }

    @Test
    void customStartAndTenMinuteIntervalAreUsedByWindowAndDedup() {
        timingOverrides = Map.of(
                "carenest.notification-reminders.attendance-start",
                "09:00",
                "carenest.notification-reminders.attendance-interval-minutes",
                10);
        assertThat(at("08:55", "14:00", true, false).remindAttendanceCutoff()).isZero();
        at("09:00", "14:00", true, false).remindAttendanceCutoff();
        at("09:05", "14:00", true, false).remindAttendanceCutoff();
        at("09:10", "14:00", true, false).remindAttendanceCutoff();
        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(notifications, times(3))
                .notifyUsers(
                        anyCollection(),
                        eq(NotificationType.ATTENDANCE_REMINDER),
                        anyString(),
                        anyString(),
                        links.capture(),
                        eq(true));
        assertThat(links.getAllValues().get(0))
                .isEqualTo(links.getAllValues().get(1))
                .isNotEqualTo(links.getAllValues().get(2));
    }
}
