package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.attendance.ClassAttendanceResponse;
import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.LeaveRequestDtos;
import com.carenest.dto.attendance.LeaveRequestDtos.CreateLeaveRequest;
import com.carenest.dto.attendance.LeaveRequestDtos.LeaveRequestResponse;
import com.carenest.dto.attendance.LeaveRequestDtos.PreviewLeaveRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.AttendanceService.ClassMealTally;
import com.carenest.service.attendance.LeaveRequestService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DayPart;
import com.carenest.utils.enums.LeavePart;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Đơn nghỉ phụ huynh (ATT-04, ATT-05, ATT-05b). Hôm nay = thứ 4, 21/10/2026; giờ chốt 08:30. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class LeaveRequestIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final LocalDate THU = TODAY.plusDays(1);
    private static final LocalDate FRI = TODAY.plusDays(2);
    private static final LocalDate NEXT_MON = TODAY.plusDays(5);
    private static final Instant BEFORE_CUTOFF = Instant.parse("2026-10-21T01:00:00Z"); // 08:00 giờ trường
    private static final Instant AFTER_CUTOFF = Instant.parse("2026-10-21T02:00:00Z"); // 09:00 giờ trường

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    static final class MovableClock extends Clock {
        private Instant now = BEFORE_CUTOFF;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Autowired
    private LeaveRequestService service;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private com.carenest.service.attendance.AttendanceDayUnlockService unlockService;

    @Autowired
    private MovableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private CurrentUser parent;
    private CurrentUser otherParent;
    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser vpB;
    private CurrentUser teacherA;
    private CurrentUser teacherB;
    private long campusA;
    private long classA;
    private long classB;
    private long child1;
    private long child2;

    @BeforeEach
    void setUp() {
        clock.now = BEFORE_CUTOFF;
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long campusB = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-B', 'B', FALSE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, year);
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2026-09-01')",
                year);
        classA = classroom(campusA, year, "Lớp A", ag3);
        classB = classroom(campusB, year, "Lớp B", ag3);
        child1 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'An', '2023-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        child2 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Bình', '2023-02-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        long child3 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Chi', '2023-03-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        enroll(child1, classA);
        enroll(child2, classA);
        enroll(child3, classB);

        parent = user("parent", CareNestRole.PARENT);
        otherParent = user("parent2", CareNestRole.PARENT);
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Mẹ (giả)', 'MOTHER', '0900000009', '000000000009', now())",
                parent.userId(),
                child1);
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vpA = user("vpa", CareNestRole.VICE_PRINCIPAL);
        vpB = user("vpb", CareNestRole.VICE_PRINCIPAL);
        campusRole(vpA, campusA);
        campusRole(vpB, campusB);
        teacherA = user("teachera", CareNestRole.TEACHER);
        teacherB = user("teacherb", CareNestRole.TEACHER);
        teach(teacherA, classA);
        teach(teacherB, classB);
    }

    @Test
    void leaveMarksSchoolDaysExcusedWithoutMealsAndShowsTagToTeacher() {
        // Thứ 4 → thứ 2 tuần sau: 4 ngày học (bỏ thứ 7, chủ nhật)
        LeaveRequestResponse leave =
                service.createLeaveRequest(parent, new CreateLeaveRequest(child1, TODAY, NEXT_MON, " Bé ốm "));

        assertThat(leave.appliedDates()).containsExactly(TODAY, THU, FRI, NEXT_MON);
        assertThat(leave.skippedDates()).isEmpty();
        assertThat(leave.reason()).isEqualTo("Bé ốm");
        assertThat(leave.status()).isEqualTo("ACTIVE");
        // OBS-31: GV lớp nhận thông báo đơn nghỉ
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notifications WHERE type = 'LEAVE_REQUEST_CREATED' AND user_id = ?",
                        Integer.class,
                        teacherA.userId()))
                .isEqualTo(1);
        ClassAttendanceResponse sheet = attendanceService.getClassAttendance(teacherA, classA, TODAY);
        ClassAttendanceResponse.ChildRow row = rowOf(sheet, child1);
        assertThat(row.record().status()).isEqualTo(AttendanceStatus.EXCUSED);
        assertThat(row.record().meals()).doesNotContainValue(true);
        assertThat(row.leave().reason()).isEqualTo("Bé ốm");
        assertThat(rowOf(sheet, child2).leave()).isNull();

        // Sổ báo ăn: trẻ báo nghỉ là vắng, không có suất
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(new SaveAttendanceRequest.Entry(
                                child2, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, true)))));
        ClassMealTally tally = attendanceService.tallyMeals(TODAY, campusA, null).stream()
                .filter(t -> t.classroomId().equals(classA))
                .findFirst()
                .orElseThrow();
        assertThat(tally.present()).isEqualTo(1);
        assertThat(tally.absent()).isEqualTo(1);
        assertThat(tally.meals().get(MealSession.LUNCH)).isEqualTo(1);

        // Đơn cả ngày mà trẻ vẫn đến: GV không đổi thành Vắng không phép, nhưng ghi được Có mặt ngay trước giờ chốt
        expectError(
                () -> attendanceService.saveClassAttendance(
                        teacherA,
                        classA,
                        new SaveAttendanceRequest(
                                TODAY,
                                List.of(new SaveAttendanceRequest.Entry(
                                        child1, AttendanceStatus.UNEXCUSED, Map.of())))),
                ApiCode.LEAVE_ATTENDANCE_LOCKED);
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(new SaveAttendanceRequest.Entry(
                                child1, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, true)))));
        ClassAttendanceResponse.ChildRow presentRow =
                rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1);
        assertThat(presentRow.record().status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(presentRow.leave()).isNotNull();
        assertThat(attendanceService.tallyMeals(TODAY, campusA, null).stream()
                        .filter(t -> t.classroomId().equals(classA))
                        .findFirst()
                        .orElseThrow()
                        .meals()
                        .get(MealSession.LUNCH))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notifications WHERE type = 'LEAVE_REQUEST_CHANGED' AND user_id = ?",
                        Integer.class,
                        parent.userId()))
                .isEqualTo(1);
    }

    @Test
    void leaveListShowsLeaveOnlyUnderTheClassChildWasInAndKeepsCancelledOnes() {
        long leaveId = service.createLeaveRequest(parent, new CreateLeaveRequest(child1, FRI, FRI, "Ốm"))
                .id();
        // Trẻ chuyển sang lớp B từ thứ 6: đơn ngày thứ 6 thuộc lớp B, lớp A không thấy
        jdbc.update("UPDATE enrollments SET valid_to = ? WHERE child_id = ?", THU, child1);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, ?)", child1, classB, FRI);
        // Sửa thẳng bằng SQL ⇒ bỏ bản Enrollment đã nạp trong phiên JPA
        entityManager.clear();

        assertThat(service.searchLeaveRequests(teacherA, null, null, TODAY, FRI, null))
                .extracting(LeaveRequestResponse::id)
                .doesNotContain(leaveId);
        assertThat(service.searchLeaveRequests(teacherB, null, null, TODAY, FRI, null))
                .filteredOn(r -> r.id().equals(leaveId))
                .extracting(LeaveRequestResponse::classId)
                .containsExactly(classB);

        // Đơn đã hủy hết vẫn hiện trong danh sách (giữ lịch sử đơn)
        service.cancelLeaveRequest(parent, leaveId);
        assertThat(service.searchLeaveRequests(teacherB, null, null, TODAY, FRI, null))
                .filteredOn(r -> r.id().equals(leaveId))
                .extracting(LeaveRequestResponse::status)
                .containsExactly("CANCELLED");
    }

    @Test
    void oneLeaveRequestCoversAtMostThirtyCalendarDays() {
        // ATT-05: 30 ngày lịch tính cả ngày đầu và ngày cuối
        assertThat(service.createLeaveRequest(
                                parent, new CreateLeaveRequest(child1, TODAY, TODAY.plusDays(29), "Về quê"))
                        .appliedDates())
                .isNotEmpty();
        expectError(
                () -> service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, TODAY.plusDays(30), TODAY.plusDays(60), "Về quê")),
                ApiCode.LEAVE_TOO_LONG);
    }

    @Test
    void onlyLinkedGuardianCanReportOrCancel() {
        expectError(
                () -> service.createLeaveRequest(otherParent, new CreateLeaveRequest(child1, THU, THU, "Ốm")),
                ApiCode.LEAVE_NOT_GUARDIAN);
        expectError(
                () -> service.createLeaveRequest(parent, new CreateLeaveRequest(child2, THU, THU, "Ốm")),
                ApiCode.LEAVE_NOT_GUARDIAN);
        LeaveRequestResponse leave = service.createLeaveRequest(parent, new CreateLeaveRequest(child1, THU, THU, "Ốm"));
        expectError(() -> service.cancelLeaveRequest(otherParent, leave.id()), ApiCode.LEAVE_NOT_GUARDIAN);
        assertThat(service.findMyLeaveRequests(parent))
                .extracting(LeaveRequestResponse::id)
                .containsExactly(leave.id());
        assertThat(service.findMyLeaveRequests(otherParent)).isEmpty();
    }

    @Test
    void afterCutoffTodayIsKeptAndLeaveAppliesFromNextSchoolDay() {
        clock.now = AFTER_CUTOFF;
        LeaveRequestResponse leave =
                service.createLeaveRequest(parent, new CreateLeaveRequest(child1, TODAY, THU, "Ốm"));

        // ATT-05b: hôm nay đã qua giờ chốt ⇒ không đổi điểm danh hôm nay
        assertThat(leave.skippedDates()).containsExactly(TODAY);
        assertThat(leave.appliedDates()).containsExactly(THU);
        expectError(
                () -> service.createLeaveRequest(parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Bổ sung")),
                ApiCode.LEAVE_AFTER_CUTOFF);
        assertThat(count("SELECT count(*) FROM attendance_records WHERE child_id = ? AND date = '2026-10-21'", child1))
                .isZero();
        // Ngày bị bỏ qua không mang nhãn đơn nghỉ, nên không khóa điểm danh của GV hôm đó (ATT-05b)
        assertThat(rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                        .leave())
                .isNull();
        assertThat(rowOf(attendanceService.getClassAttendance(teacherA, classA, THU), child1)
                        .leave())
                .isNotNull();

        // Chỉ thứ 7, chủ nhật ⇒ không có ngày học
        expectError(
                () -> service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, TODAY.plusDays(3), TODAY.plusDays(4), "Ốm")),
                ApiCode.LEAVE_NO_SCHOOL_DAY);
        expectError(
                () -> service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, TODAY.minusDays(1), TODAY, "Ốm")),
                ApiCode.LEAVE_DATE_IN_PAST);
        expectError(
                () -> service.createLeaveRequest(parent, new CreateLeaveRequest(child1, FRI, THU, "Ốm")),
                ApiCode.LEAVE_DATE_RANGE_INVALID);
    }

    @Test
    void afterCutoffAfternoonLeaveIsNoticeOnlyAndCanBeCancelledWithoutChangingAttendance() {
        clock.now = AFTER_CUTOFF;

        LeaveRequestResponse leave = service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Đón sớm", LeavePart.AFTERNOON, true));

        assertThat(leave.noticeOnly()).isTrue();
        assertThat(leave.appliedDates()).isEmpty();
        assertThat(leave.skippedDates()).containsExactly(TODAY);
        assertThat(count("SELECT count(*) FROM attendance_records WHERE child_id = ? AND date = '2026-10-21'", child1))
                .isZero();
        ClassAttendanceResponse.ChildRow row =
                rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1);
        assertThat(row.leave().noticeOnly()).isTrue();
        assertThat(row.record()).isNull();

        LeaveRequestResponse cancelled = service.cancelLeaveRequest(parent, leave.id());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(count("SELECT count(*) FROM attendance_records WHERE child_id = ? AND date = '2026-10-21'", child1))
                .isZero();
    }

    @Test
    void afternoonNoticeClosesAtAfternoonStartAndIsRefusedForAbsentChild() {
        // ATT-05b (Cần chốt 25/11/2026): gửi / hủy báo nghỉ chiều hôm nay chỉ trước giờ bắt đầu buổi chiều 13:30
        clock.now = AFTER_CUTOFF;
        LeaveRequestResponse leave = service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Đón sớm", LeavePart.AFTERNOON, true));
        clock.now = Instant.parse("2026-10-21T06:30:00Z");
        expectError(() -> service.cancelLeaveRequest(parent, leave.id()), ApiCode.LEAVE_AFTERNOON_NOTICE_CLOSED);
        expectError(
                () -> service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Đón sớm", LeavePart.AFTERNOON, true)),
                ApiCode.LEAVE_AFTERNOON_NOTICE_CLOSED);
        // Đơn cho ngày mai vẫn gửi bình thường
        assertThat(service.createLeaveRequest(
                                parent,
                                new CreateLeaveRequest(child1, TODAY.plusDays(1), TODAY.plusDays(1), "Ốm", null, null))
                        .noticeOnly())
                .isFalse();
    }

    @Test
    void afternoonNoticeIsRefusedWhenChildIsAlreadyAbsentToday() {
        clock.now = BEFORE_CUTOFF;
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.UNEXCUSED, Map.of()))));
        clock.now = AFTER_CUTOFF;
        expectError(
                () -> service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Đón sớm", LeavePart.AFTERNOON, true)),
                ApiCode.LEAVE_CHILD_ABSENT_TODAY);
    }

    @Test
    void cancellingAfternoonLeaveRestoresTheAttendanceRecordBeforeTheLeave() {
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, Map.of()))));
        LeaveRequestResponse leave = service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Đón sớm", LeavePart.AFTERNOON, true));
        ClassAttendanceResponse.RecordView duringLeave = rowOf(
                        attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record();
        assertThat(duringLeave.absentPart()).isEqualTo(DayPart.AFTERNOON);
        assertThat(duringLeave.meals().get(MealSession.AFTERNOON)).isFalse();

        service.cancelLeaveRequest(parent, leave.id());

        ClassAttendanceResponse.RecordView restored = rowOf(
                        attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record();
        assertThat(restored.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(restored.absentPart()).isNull();
        assertThat(restored.meals().get(MealSession.LUNCH)).isTrue();
        assertThat(restored.meals().get(MealSession.AFTERNOON)).isTrue();
    }

    @Test
    void cancellingLegacyLeaveWithoutEffectRowsFallsBackToOldRevert() {
        LeaveRequestResponse leave =
                service.createLeaveRequest(parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Nghỉ"));
        assertThat(rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                        .record()
                        .status())
                .isEqualTo(AttendanceStatus.EXCUSED);
        // Đơn gửi trước V201: không có dòng leave_attendance_effects
        jdbc.update("DELETE FROM leave_attendance_effects WHERE leave_request_id = ?", leave.id());

        service.cancelLeaveRequest(parent, leave.id());

        assertThat(rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                        .record())
                .isNull();
    }

    @Test
    void cancellingAfternoonLeaveSentBeforeAttendanceReturnsTheAfternoonMeal() {
        // Đơn nghỉ chiều gửi khi chưa có điểm danh: chưa có dòng hiệu ứng; GV điểm danh sau đó, rồi phụ huynh hủy đơn
        LeaveRequestResponse leave = service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Về quê", LeavePart.AFTERNOON, true));
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, Map.of()))));
        ClassAttendanceResponse.RecordView duringLeave = rowOf(
                        attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record();
        assertThat(duringLeave.absentPart()).isEqualTo(DayPart.AFTERNOON);
        assertThat(duringLeave.meals().get(MealSession.AFTERNOON)).isFalse();

        service.cancelLeaveRequest(parent, leave.id());

        ClassAttendanceResponse.RecordView after = rowOf(
                        attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record();
        assertThat(after.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(after.absentPart()).isNull();
        assertThat(after.meals().get(MealSession.LUNCH)).isTrue();
        assertThat(after.meals().get(MealSession.AFTERNOON)).isTrue();
    }

    @Test
    void cancellingAfternoonLeaveAfterTeacherEditKeepsTeacherDataAndClearsLeavePart() {
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, Map.of()))));
        LeaveRequestResponse leave = service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Đón sớm", LeavePart.AFTERNOON, true));
        // GV sửa lại điểm danh sau khi đơn đã áp (bản ghi đổi recordedAt)
        clock.now = BEFORE_CUTOFF.plusSeconds(600);
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, Map.of()))));

        service.cancelLeaveRequest(parent, leave.id());

        ClassAttendanceResponse.RecordView after = rowOf(
                        attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record();
        assertThat(after.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(after.recordedBy()).isEqualTo(teacherA.userId());
        assertThat(after.absentPart()).isNull();
        assertThat(count("SELECT count(*) FROM leave_attendance_effects WHERE leave_request_id = ?", leave.id()))
                .isZero();
    }

    @Test
    void overlappingLeaveIsRejected() {
        service.createLeaveRequest(parent, new CreateLeaveRequest(child1, THU, FRI, "Về quê"));

        expectError(
                () -> service.createLeaveRequest(parent, new CreateLeaveRequest(child1, FRI, NEXT_MON, "Ốm")),
                ApiCode.LEAVE_OVERLAP);
        assertThat(service.createLeaveRequest(parent, new CreateLeaveRequest(child1, NEXT_MON, NEXT_MON, "Ốm"))
                        .appliedDates())
                .containsExactly(NEXT_MON);
    }

    @Test
    void cancelRemovesDaysBeforeCutoffAndKeepsLockedOnes() {
        LeaveRequestResponse leave =
                service.createLeaveRequest(parent, new CreateLeaveRequest(child1, TODAY, FRI, "Ốm"));
        clock.now = AFTER_CUTOFF;

        LeaveRequestResponse cancelled = service.cancelLeaveRequest(parent, leave.id());

        // Hôm nay đã qua giờ chốt ⇒ giữ Vắng có phép; thứ 5, thứ 6 bỏ ghi
        assertThat(cancelled.effectiveTo()).isEqualTo(TODAY);
        assertThat(count("SELECT count(*) FROM attendance_records WHERE child_id = ?", child1))
                .isEqualTo(1);
        assertThat(rowOf(attendanceService.getClassAttendance(teacherA, classA, THU), child1)
                        .leave())
                .isNull();
        // Ngày trống lại cho phép gửi đơn mới
        assertThat(service.createLeaveRequest(parent, new CreateLeaveRequest(child1, FRI, FRI, "Ốm"))
                        .appliedDates())
                .containsExactly(FRI);
        expectError(() -> service.cancelLeaveRequest(parent, cancelled.id()), ApiCode.LEAVE_CANNOT_CANCEL);
    }

    @Test
    void staffSeeLeaveRequestsInTheirScope() {
        long leaveId = service.createLeaveRequest(parent, new CreateLeaveRequest(child1, THU, THU, "Ốm"))
                .id();

        assertThat(service.searchLeaveRequests(teacherA, null, null, TODAY, FRI, null))
                .extracting(LeaveRequestResponse::id)
                .containsExactly(leaveId);
        assertThat(service.searchLeaveRequests(teacherB, null, null, TODAY, FRI, null))
                .isEmpty();
        assertThat(service.searchLeaveRequests(vpA, null, campusA, TODAY, FRI, null))
                .hasSize(1);
        assertThat(service.searchLeaveRequests(principal, classA, null, TODAY, FRI, null))
                .extracting(LeaveRequestResponse::childName)
                .containsExactly("An");
        expectError(
                () -> service.searchLeaveRequests(teacherB, classA, null, TODAY, FRI, null),
                ApiCode.CLASS_ACCESS_DENIED);
        // PHT mặc định chỉ xem campus được phân công.
        assertThat(service.searchLeaveRequests(vpB, null, campusA, TODAY, FRI, null))
                .isEmpty();
        // Khi HT cấp ATTENDANCE_VIEW phạm vi toàn trường thì PHT xem được campus khác.
        CurrentUser vpBSchoolWide = new CurrentUser(
                vpB.userId(),
                vpB.keycloakUserId(),
                vpB.username(),
                vpB.fullName(),
                vpB.email(),
                vpB.roles(),
                vpB.permissions(),
                Set.of(Permission.ATTENDANCE_VIEW));
        assertThat(service.searchLeaveRequests(vpBSchoolWide, null, campusA, TODAY, FRI, null))
                .hasSize(1);

        // Trẻ đã rời lớp trước hôm nay vẫn thấy đơn trong khoảng trẻ còn học lớp (OBS-23)
        jdbc.update("UPDATE enrollments SET valid_to = ? WHERE child_id = ?", TODAY.minusDays(1), child1);
        assertThat(service.searchLeaveRequests(teacherA, null, null, TODAY.minusDays(7), FRI, null))
                .extracting(LeaveRequestResponse::id)
                .containsExactly(leaveId);
    }

    private static ClassAttendanceResponse.ChildRow rowOf(ClassAttendanceResponse sheet, long childId) {
        return sheet.children().stream()
                .filter(r -> r.child().id().equals(childId))
                .findFirst()
                .orElseThrow();
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions =
                switch (role) {
                    case TEACHER, TEAM_LEADER -> Set.of(Permission.ATTENDANCE_VIEW, Permission.ATTENDANCE_RECORD);
                    case PRINCIPAL, VICE_PRINCIPAL -> Set.of(Permission.ATTENDANCE_VIEW);
                    default -> Set.of();
                };
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    private void campusRole(CurrentUser user, long campusId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                user.userId(),
                campusId);
    }

    @Test
    void cancellingLeavePreservesTeacherAuditAndAllowsFreshAttendance() {
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, Map.of()))));
        long oldRecord = rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record()
                .id();
        LeaveRequestResponse leave =
                service.createLeaveRequest(parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Nghỉ"));
        service.cancelLeaveRequest(parent, leave.id());
        assertThat(count("SELECT count(*) FROM attendance_record_history WHERE record_id = ?", oldRecord))
                .isEqualTo(3);
        // V201: hủy đơn chỉ hoàn tác phần đơn đã đổi ⇒ trả về điểm danh Có mặt giáo viên đã ghi trước đó
        var restored = rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record();
        assertThat(restored.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(restored.recordedBy()).isEqualTo(teacherA.userId());
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, Map.of()))));
        assertThat(rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                        .record()
                        .status())
                .isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void morningLeaveWaitsForArrivalThenClosesAsFullDayAbsence() {
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Bố (giả)', 'FATHER', '0900000008', '000000000008', now())",
                parent.userId(),
                child2);
        service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Đi khám", LeavePart.MORNING, true));
        service.createLeaveRequest(
                parent, new CreateLeaveRequest(child2, TODAY, TODAY, "Đi khám", LeavePart.MORNING, false));

        // ATT-13: nghỉ sáng ⇒ có phép buổi sáng, chờ đến buổi chiều; bữa xế theo công tắc ăn
        ClassAttendanceResponse.ChildRow row =
                rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1);
        assertThat(row.record().status()).isEqualTo(AttendanceStatus.EXCUSED);
        assertThat(row.record().absentPart()).isEqualTo(DayPart.MORNING);
        assertThat(row.record().meals().get(MealSession.AFTERNOON)).isTrue();
        assertThat(row.leave().part()).isEqualTo(LeavePart.MORNING);
        assertThat(rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child2)
                        .record()
                        .meals())
                .doesNotContainValue(true);
        ClassMealTally tally = attendanceService.tallyMeals(TODAY, campusA, null).stream()
                .filter(t -> t.classroomId().equals(classA))
                .findFirst()
                .orElseThrow();
        assertThat(tally.meals().get(MealSession.AFTERNOON)).isEqualTo(1);
        assertThat(tally.meals().get(MealSession.LUNCH)).isZero();

        // GV không tự điểm danh trẻ nghỉ sáng; "Đã đến" chỉ từ giờ chốt
        expectError(
                () -> attendanceService.saveClassAttendance(
                        teacherA,
                        classA,
                        new SaveAttendanceRequest(
                                TODAY,
                                List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, Map.of())))),
                ApiCode.LEAVE_ATTENDANCE_LOCKED);
        // Trước giờ chốt GV chỉ đổi công tắc ăn bữa xế, giữ trạng thái theo đơn
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(new SaveAttendanceRequest.Entry(
                                child2, AttendanceStatus.EXCUSED, Map.of(MealSession.AFTERNOON, true)))));
        ClassAttendanceResponse.RecordView xe = rowOf(
                        attendanceService.getClassAttendance(teacherA, classA, TODAY), child2)
                .record();
        assertThat(xe.status()).isEqualTo(AttendanceStatus.EXCUSED);
        assertThat(xe.absentPart()).isEqualTo(DayPart.MORNING);
        assertThat(xe.meals().get(MealSession.AFTERNOON)).isTrue();
        expectError(
                () -> attendanceService.saveClassAttendance(
                        teacherA,
                        classA,
                        new SaveAttendanceRequest(
                                TODAY,
                                List.of(new SaveAttendanceRequest.Entry(
                                        child2, AttendanceStatus.EXCUSED, Map.of(MealSession.LUNCH, true))))),
                ApiCode.HALF_DAY_LEAVE_MEAL);
        expectError(
                () -> attendanceService.markArrived(teacherA, classA, child1, TODAY),
                ApiCode.ATTENDANCE_ARRIVAL_NOT_ALLOWED);
        clock.now = Instant.parse("2026-10-21T06:40:00Z"); // 13:40 giờ trường, trong khung nhận trẻ chiều
        attendanceService.markArrived(teacherA, classA, child1, TODAY);
        assertThat(rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                        .record()
                        .status())
                .isEqualTo(AttendanceStatus.PRESENT);

        // Qua 14:00 trẻ chưa đến ⇒ Vắng cả ngày, báo phụ huynh và GV
        clock.now = Instant.parse("2026-10-21T07:05:00Z");
        assertThat(attendanceService.closeMorningLeaves()).isEqualTo(1);
        assertThat(rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child2)
                        .record()
                        .status())
                .isEqualTo(AttendanceStatus.UNEXCUSED);
        assertThat(count(
                        "SELECT count(*) FROM notifications WHERE title = 'Bé chưa đến buổi chiều' AND user_id = ?",
                        parent.userId()))
                .isEqualTo(1);
        expectError(
                () -> attendanceService.markArrived(teacherA, classA, child2, TODAY),
                ApiCode.ATTENDANCE_ARRIVAL_NOT_ALLOWED);

        // ATT-14: nửa buổi có mặt tính 0,5 ngày
        var summary = attendanceService.getClassSummary(principal, classA, TODAY, TODAY);
        var total = summary.children().stream()
                .filter(c -> c.child().id().equals(child1))
                .findFirst()
                .orElseThrow();
        assertThat(total.attendedDays()).isEqualTo(0.5);
        assertThat(total.morningOff()).isEqualTo(1);
        assertThat(summary.days().getFirst().morningOff()).isEqualTo(2);
    }

    @Test
    void unexcusedMorningAbsenceCanArriveInAfternoonWithoutChangingMeals() {
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.UNEXCUSED, Map.of()))));
        ClassAttendanceResponse.RecordView before = rowOf(
                        attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record();
        assertThat(before.status()).isEqualTo(AttendanceStatus.UNEXCUSED);
        assertThat(before.meals()).doesNotContainValue(true);

        clock.now = Instant.parse("2026-10-21T06:40:00Z"); // 13:40 giờ trường, trong khung nhận trẻ chiều
        attendanceService.markArrived(teacherA, classA, child1, TODAY);

        ClassAttendanceResponse.RecordView after = rowOf(
                        attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record();
        assertThat(after.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(after.absentPart()).isEqualTo(DayPart.MORNING);
        assertThat(after.absentPartUnexcused()).isTrue();
        assertThat(after.meals()).doesNotContainValue(true);
        var total = attendanceService.getClassSummary(principal, classA, TODAY, TODAY).children().stream()
                .filter(c -> c.child().id().equals(child1))
                .findFirst()
                .orElseThrow();
        assertThat(total.attendedDays()).isEqualTo(0.5);
    }

    @Test
    void reopenedDayRecordsPresentPartInOneStep() {
        clock.now = AFTER_CUTOFF;
        reopenToday();

        // Mở lại: quên điểm danh, bé An chỉ đến chiều, bé Bình chỉ học sáng — mỗi trẻ một lần bấm
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(
                                new SaveAttendanceRequest.Entry(
                                        child1, AttendanceStatus.PRESENT, null, null, LeavePart.AFTERNOON),
                                new SaveAttendanceRequest.Entry(
                                        child2, AttendanceStatus.PRESENT, null, null, LeavePart.MORNING))));

        ClassAttendanceResponse sheet = attendanceService.getClassAttendance(teacherA, classA, TODAY);
        ClassAttendanceResponse.RecordView an = rowOf(sheet, child1).record();
        assertThat(an.absentPart()).isEqualTo(DayPart.MORNING);
        assertThat(an.absentPartUnexcused()).isTrue();
        assertThat(an.meals().get(MealSession.LUNCH)).isNotEqualTo(Boolean.TRUE);
        ClassAttendanceResponse.RecordView binh = rowOf(sheet, child2).record();
        assertThat(binh.absentPart()).isEqualTo(DayPart.AFTERNOON);
        assertThat(binh.absentPartUnexcused()).isFalse();
        assertThat(attendedDays(child1)).isEqualTo(0.5);
        assertThat(attendedDays(child2)).isEqualTo(0.5);

        // Sửa lại bé An thành cả ngày cũng một lần bấm
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(new SaveAttendanceRequest.Entry(
                                child1, AttendanceStatus.PRESENT, null, null, LeavePart.FULL_DAY))));
        ClassAttendanceResponse.RecordView fixed = rowOf(
                        attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record();
        assertThat(fixed.absentPart()).isNull();
        assertThat(fixed.absentPartUnexcused()).isFalse();
        assertThat(attendedDays(child1)).isEqualTo(1.0);
    }

    @Test
    void presentPartNeedsReopenedDay() {
        expectError(
                () -> attendanceService.saveClassAttendance(
                        teacherA,
                        classA,
                        new SaveAttendanceRequest(
                                TODAY,
                                List.of(new SaveAttendanceRequest.Entry(
                                        child1, AttendanceStatus.PRESENT, null, null, LeavePart.MORNING)))),
                ApiCode.ATTENDANCE_PART_REQUIRES_UNLOCK);
    }

    @Test
    void fullDayLeaveChildCanBeMarkedPresentWhenReopened() {
        service.createLeaveRequest(parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Ốm"));
        clock.now = AFTER_CUTOFF;
        reopenToday();

        // Đơn cả ngày chỉ đổi thành Có mặt (trẻ vẫn đến), không thành Vắng không phép
        expectError(
                () -> attendanceService.saveClassAttendance(
                        teacherA,
                        classA,
                        new SaveAttendanceRequest(
                                TODAY,
                                List.of(new SaveAttendanceRequest.Entry(
                                        child1, AttendanceStatus.UNEXCUSED, Map.of())))),
                ApiCode.LEAVE_ATTENDANCE_LOCKED);
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, null))));

        ClassAttendanceResponse.ChildRow row =
                rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1);
        assertThat(row.record().status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(row.record().absentPart()).isNull();
        assertThat(row.leave()).isNotNull();
        assertThat(attendedDays(child1)).isEqualTo(1.0);
    }

    private void reopenToday() {
        CurrentUser unlocker = new CurrentUser(
                principal.userId(),
                "kc-principal",
                "principal",
                "principal",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                Set.of(Permission.ATTENDANCE_VIEW, Permission.ATTENDANCE_UNLOCK));
        unlockService.unlockDay(
                unlocker, new DayUnlockRequest(classA, TODAY, AFTER_CUTOFF.plusSeconds(3600), "GV sửa điểm danh"));
    }

    private double attendedDays(long childId) {
        return attendanceService.getClassSummary(principal, classA, TODAY, TODAY).children().stream()
                .filter(c -> c.child().id().equals(childId))
                .findFirst()
                .orElseThrow()
                .attendedDays();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "ATT-12: nghỉ buổi sáng nhiều ngày ghi từng ngày học, giữ bữa xế theo công tắc ăn")
    void morningLeaveCoversSeveralSchoolDays() {
        LeaveRequestResponse leave = service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, THU, FRI, "Học năng khiếu", LeavePart.MORNING, true));

        assertThat(leave.appliedDates()).containsExactly(THU, FRI);
        entityManager.flush();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM attendance_records WHERE child_id = ? AND absent_part = 'MORNING'",
                        Integer.class,
                        child1))
                .isEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "Đã nghỉ sáng, sau giờ chốt báo thêm nghỉ chiều: cả ngày có phép, suất giữ nguyên")
    void afternoonNoticeExtendsMorningLeave() {
        service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Đi khám", LeavePart.MORNING, true));
        List<String> mealsBefore = mealsOfToday(child1);
        clock.now = AFTER_CUTOFF;

        LeaveRequestResponse notice = service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Ở nhà cả ngày", LeavePart.AFTERNOON, null));

        assertThat(absentPartToday(child1)).isNull();
        assertThat(mealsOfToday(child1)).isEqualTo(mealsBefore);

        service.cancelLeaveRequest(parent, notice.id());

        assertThat(absentPartToday(child1)).isEqualTo("MORNING");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Cô chỉ đổi bữa rồi phụ huynh hủy đơn nghỉ sáng: không còn Vắng có phép")
    void cancellingMorningLeaveAfterMealToggleRemovesExcused() {
        LeaveRequestResponse leave = service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Đi khám", LeavePart.MORNING, true));
        jdbc.update(
                "UPDATE attendance_records SET recorded_at = recorded_at + interval '1 minute', meals = ''"
                        + " WHERE child_id = ? AND date = ?",
                child1,
                TODAY);
        entityManager.clear();

        service.cancelLeaveRequest(parent, leave.id());

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM attendance_records WHERE child_id = ? AND date = ?",
                        Integer.class,
                        child1,
                        TODAY))
                .isZero();
    }

    private String absentPartToday(long childId) {
        entityManager.flush();
        return jdbc.queryForObject(
                "SELECT absent_part FROM attendance_records WHERE child_id = ? AND date = ?",
                String.class,
                childId,
                TODAY);
    }

    private List<String> mealsOfToday(long childId) {
        entityManager.flush();
        return jdbc.queryForList(
                "SELECT meals FROM attendance_records WHERE child_id = ? AND date = ?", String.class, childId, TODAY);
    }

    @Test
    void afternoonLeaveKeepsMorningAttendanceWithoutAfternoonMeal() {
        service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Về quê", LeavePart.AFTERNOON, true));

        expectError(
                () -> attendanceService.saveClassAttendance(
                        teacherA,
                        classA,
                        new SaveAttendanceRequest(
                                TODAY,
                                List.of(new SaveAttendanceRequest.Entry(
                                        child1, AttendanceStatus.PRESENT, Map.of(MealSession.AFTERNOON, true))))),
                ApiCode.HALF_DAY_LEAVE_MEAL);
        // Câu báo riêng cho buổi nghỉ (Cần chốt 19/11/2026)
        assertThatThrownBy(() -> attendanceService.saveClassAttendance(
                        teacherA,
                        classA,
                        new SaveAttendanceRequest(
                                TODAY,
                                List.of(new SaveAttendanceRequest.Entry(
                                        child1, AttendanceStatus.PRESENT, Map.of(MealSession.AFTERNOON, true))))))
                .hasMessage("Trẻ nghỉ buổi chiều, không báo bữa Xế");
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, Map.of()))));
        ClassAttendanceResponse.RecordView view = rowOf(
                        attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                .record();
        assertThat(view.absentPart()).isEqualTo(DayPart.AFTERNOON);
        assertThat(view.meals().get(MealSession.LUNCH)).isTrue();
        assertThat(view.meals().get(MealSession.AFTERNOON)).isFalse();

        clock.now = AFTER_CUTOFF;
        var total = attendanceService.getClassSummary(principal, classA, TODAY, TODAY).children().stream()
                .filter(c -> c.child().id().equals(child1))
                .findFirst()
                .orElseThrow();
        assertThat(total.attendedDays()).isEqualTo(0.5);
        assertThat(total.afternoonOff()).isEqualTo(1);
        assertThat(total.mealCounts()).containsEntry(MealSession.LUNCH, 1).doesNotContainKey(MealSession.AFTERNOON);
    }

    @Test
    void afternoonLeaveAllowedForGroupWithoutAfternoonMeal() {
        // Cần chốt 18/11/2026: nhóm không có Xế vẫn xin nghỉ chiều (đón sớm), suất ăn không đổi, chuyên cần 0,5
        jdbc.update("UPDATE age_groups SET meal_sessions = 'LUNCH' WHERE code = 'ag-3'");
        jdbc.update(
                "UPDATE school_year_age_group_members SET meal_sessions='LUNCH' WHERE age_group_id=(SELECT id FROM age_groups WHERE code='ag-3')");
        LeaveRequestResponse leave = service.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY, TODAY, "Đón sớm", LeavePart.AFTERNOON, true));
        assertThat(leave.part()).isEqualTo(LeavePart.AFTERNOON);
        // Ô "Bé tham gia ăn" ghi rõ bữa của buổi còn học
        assertThat(leave.eats()).isTrue();
        assertThat(leave.eatsMeals()).containsExactly(MealSession.LUNCH);
        // Nghỉ sáng ở nhóm không có Xế: buổi chiều không có bữa ⇒ công tắc ăn không áp dụng
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Bố (giả)', 'FATHER', '0900000008', '000000000008', now())",
                parent.userId(),
                child2);
        LeaveRequestResponse morning = service.createLeaveRequest(
                parent, new CreateLeaveRequest(child2, TODAY, TODAY, "Đi khám", LeavePart.MORNING, false));
        assertThat(morning.eats()).isNull();
        assertThat(morning.eatsMeals()).isEmpty();
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY, List.of(new SaveAttendanceRequest.Entry(child1, AttendanceStatus.PRESENT, Map.of()))));
        ClassAttendanceResponse.ChildRow row =
                rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1);
        assertThat(row.leave().part()).isEqualTo(LeavePart.AFTERNOON);
        assertThat(row.record().absentPart()).isEqualTo(DayPart.AFTERNOON);
        assertThat(row.record().meals()).containsEntry(MealSession.LUNCH, true);
        clock.now = AFTER_CUTOFF;
        var total = attendanceService.getClassSummary(principal, classA, TODAY, TODAY).children().stream()
                .filter(c -> c.child().id().equals(child1))
                .findFirst()
                .orElseThrow();
        assertThat(total.attendedDays()).isEqualTo(0.5);
        assertThat(total.mealCounts()).containsEntry(MealSession.LUNCH, 1);
    }

    @Test
    void leaveCannotExtendBeyondSchoolYear() {
        expectError(
                () -> service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, TODAY, LocalDate.of(2035, 1, 1), "Nghỉ")),
                ApiCode.LEAVE_OUTSIDE_SCHOOL_YEAR);
        assertThatThrownBy(() -> service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, TODAY, LocalDate.of(2035, 1, 1), "Nghỉ")))
                .hasMessageStartingWith("Đơn nghỉ phải nằm trong năm học (");
    }

    @Test
    void leaveCannotExtendBeyondSecondSemesterEnd() {
        // Năm học tới 31/05/2027 nhưng học kỳ II kết thúc 15/05/2027: đơn chỉ nhận đến hết học kỳ II
        jdbc.update("UPDATE school_years SET semester1_start = '2026-09-01', semester1_end = '2027-01-15', "
                + "semester2_start = '2027-02-01', semester2_end = '2027-05-15' WHERE name = '2026-2027'");
        LocalDate secondSemesterEnd = LocalDate.of(2027, 5, 15);
        LocalDate dayAfter = secondSemesterEnd.plusDays(1);

        expectError(
                () -> service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, secondSemesterEnd, dayAfter, "Nghỉ hè")),
                ApiCode.LEAVE_OUTSIDE_SCHOOL_YEAR);
        assertThatThrownBy(() -> service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, secondSemesterEnd, dayAfter, "Nghỉ hè")))
                .hasMessage("Đơn nghỉ phải nằm trong năm học (01/09/2026 – 15/05/2027)");
        // Ngày cuối học kỳ II vẫn gửi được (15/05/2027 là thứ Bảy nên chọn thứ Sáu 14/05/2027)
        assertThat(service.createLeaveRequest(
                                parent,
                                new CreateLeaveRequest(
                                        child1, secondSemesterEnd.minusDays(1), secondSemesterEnd.minusDays(1), "Nghỉ"))
                        .id())
                .isNotNull();
    }

    @Test
    void leaveDaysAfterChildLeavesSchoolAreNotCountedOrListed() {
        LeaveRequestResponse leave =
                service.createLeaveRequest(parent, new CreateLeaveRequest(child1, TODAY, FRI, "Nghỉ"));
        // Trẻ nghỉ học hẳn từ thứ Năm: học tới hết hôm nay, các bản ghi Vắng có phép tạo sẵn cho THU, FRI còn trong DB
        jdbc.update("UPDATE enrollments SET valid_to = ? WHERE child_id = ?", TODAY, child1);
        entityManager.clear();

        assertThat(attendanceService.findClassDays(List.of(classA), TODAY, FRI).stream()
                        .filter(d -> d.childId().equals(child1))
                        .map(AttendanceService.ClassChildDay::date))
                .containsExactly(TODAY);
        assertThat(service.searchLeaveRequests(principal, null, null, TODAY, FRI, null).stream()
                        .filter(r -> r.id().equals(leave.id()))
                        .flatMap(r -> r.days().stream())
                        .map(LeaveRequestDtos.LeaveDay::date))
                .containsExactly(TODAY);
    }

    @Test
    void leaveRecordsFollowTheClassChildWasInOnEachDay() {
        service.createLeaveRequest(parent, new CreateLeaveRequest(child1, TODAY, FRI, "Nghỉ"));
        // Trẻ chuyển sang lớp B từ thứ Sáu: bản ghi Vắng có phép của thứ Sáu đã tạo sẵn với lớp A
        jdbc.update("UPDATE enrollments SET valid_to = ? WHERE child_id = ?", THU, child1);
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, ?)", child1, classB, FRI);
        entityManager.clear();

        assertThat(attendanceService.findClassDays(List.of(classA), TODAY, FRI).stream()
                        .filter(d -> d.childId().equals(child1))
                        .map(AttendanceService.ClassChildDay::date))
                .containsExactly(TODAY, THU);
        var inB = attendanceService.findClassDays(List.of(classB), TODAY, FRI).stream()
                .filter(d -> d.childId().equals(child1))
                .toList();
        assertThat(inB).extracting(AttendanceService.ClassChildDay::date).containsExactly(FRI);
        assertThat(inB).extracting(AttendanceService.ClassChildDay::classroomId).containsExactly(classB);
    }

    @Test
    void leaveFromScheduledWithdrawalDateIsRejectedForSubmitAndPreview() {
        LocalDate scheduledLeaveOn = LocalDate.of(2026, 11, 27);
        LocalDate dayBeforeScheduledLeave = scheduledLeaveOn.minusDays(1);
        jdbc.update(
                "UPDATE children SET scheduled_leave_on = ?, scheduled_leave_reason = 'Chuyển trường' WHERE id = ?",
                scheduledLeaveOn,
                child1);

        expectError(
                () -> service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, dayBeforeScheduledLeave, scheduledLeaveOn, "Nghỉ")),
                ApiCode.LEAVE_CHILD_LEAVING);
        expectError(
                () -> service.previewLeaveRequest(
                        parent,
                        new PreviewLeaveRequest(child1, dayBeforeScheduledLeave, scheduledLeaveOn, null, null, null)),
                ApiCode.LEAVE_CHILD_LEAVING);
        assertThat(service.createLeaveRequest(
                                parent,
                                new CreateLeaveRequest(
                                        child1, dayBeforeScheduledLeave, dayBeforeScheduledLeave, "Nghỉ"))
                        .appliedDates())
                .containsExactly(dayBeforeScheduledLeave);
    }

    private void teach(CurrentUser user, long classroomId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                user.userId(),
                classroomId);
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusId
                + ", " + yearId + ", '" + name + "', " + ageGroupId + ") RETURNING id");
    }

    private void enroll(long childId, long classroomId) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                childId,
                classroomId);
    }

    private long count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Long.class, arg);
    }

    private static void expectError(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
