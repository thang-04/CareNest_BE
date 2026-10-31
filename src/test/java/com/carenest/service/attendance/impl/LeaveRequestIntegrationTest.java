package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.attendance.ClassAttendanceResponse;
import com.carenest.dto.attendance.LeaveRequestDtos.CreateLeaveRequest;
import com.carenest.dto.attendance.LeaveRequestDtos.LeaveRequestResponse;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.AttendanceService.ClassMealTally;
import com.carenest.service.attendance.LeaveRequestService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
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
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

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
    private MovableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

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
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2026-09-01')",
                year);
        classA = classroom(campusA, year, "Lớp A", ag3);
        classB = classroom(campusB, year, "Lớp B", ag3);
        child1 = insertId("INSERT INTO children (full_name, date_of_birth) VALUES ('An', '2023-01-01') RETURNING id");
        child2 = insertId("INSERT INTO children (full_name, date_of_birth) VALUES ('Bình', '2023-02-01') RETURNING id");
        long child3 =
                insertId("INSERT INTO children (full_name, date_of_birth) VALUES ('Chi', '2023-03-01') RETURNING id");
        enroll(child1, classA);
        enroll(child2, classA);
        enroll(child3, classB);

        parent = user("parent", CareNestRole.PARENT);
        otherParent = user("parent2", CareNestRole.PARENT);
        jdbc.update(
                "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'MOTHER')",
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

        // P-20: trẻ vẫn đến lớp ⇒ GV sửa Có mặt trước giờ chốt như thường
        attendanceService.saveClassAttendance(
                teacherA,
                classA,
                new SaveAttendanceRequest(
                        TODAY,
                        List.of(new SaveAttendanceRequest.Entry(
                                child1, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, true)))));
        assertThat(rowOf(attendanceService.getClassAttendance(teacherA, classA, TODAY), child1)
                        .record()
                        .status())
                .isEqualTo(AttendanceStatus.PRESENT);
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
        assertThat(count("SELECT count(*) FROM attendance_records WHERE child_id = ? AND date = '2026-10-21'", child1))
                .isZero();

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

        assertThat(service.searchLeaveRequests(teacherA, null, null, TODAY, FRI))
                .extracting(LeaveRequestResponse::id)
                .containsExactly(leaveId);
        assertThat(service.searchLeaveRequests(teacherB, null, null, TODAY, FRI))
                .isEmpty();
        assertThat(service.searchLeaveRequests(vpA, null, campusA, TODAY, FRI)).hasSize(1);
        assertThat(service.searchLeaveRequests(principal, classA, null, TODAY, FRI))
                .extracting(LeaveRequestResponse::childName)
                .containsExactly("An");
        expectError(() -> service.searchLeaveRequests(teacherB, classA, null, TODAY, FRI), ApiCode.CLASS_ACCESS_DENIED);
        expectError(() -> service.searchLeaveRequests(vpB, null, campusA, TODAY, FRI), ApiCode.CAMPUS_ACCESS_DENIED);
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
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role));
    }

    private void campusRole(CurrentUser user, long campusId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                user.userId(),
                campusId);
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
