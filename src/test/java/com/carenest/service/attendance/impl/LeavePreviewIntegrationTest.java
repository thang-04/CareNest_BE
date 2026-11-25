package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.attendance.LeaveRequestDtos.CreateLeaveRequest;
import com.carenest.dto.attendance.LeaveRequestDtos.LeavePreviewResponse;
import com.carenest.dto.attendance.LeaveRequestDtos.LeaveRequestResponse;
import com.carenest.dto.attendance.LeaveRequestDtos.PreviewLeaveRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.LeaveRequestService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.LeavePart;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.Permission;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * Xem trước đơn nghỉ (B1), bữa hủy trên đơn (B2), GV đã xem đơn (B3) — kịch bản P1–P13 của App. Hôm nay = thứ 4,
 * 21/10/2026; giờ chốt 08:30; nhóm tuổi ag-3 có bữa Trưa, Xế.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class LeavePreviewIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final LocalDate THU = TODAY.plusDays(1);
    private static final LocalDate FRI = TODAY.plusDays(2);
    private static final LocalDate NEXT_MON = TODAY.plusDays(5);
    private static final Instant BEFORE_CUTOFF = Instant.parse("2026-10-21T01:00:00Z");
    private static final Instant AFTER_CUTOFF = Instant.parse("2026-10-21T02:00:00Z");

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
    private MovableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser parent;
    private CurrentUser otherParent;
    private CurrentUser principal;
    private CurrentUser teacherA;
    private long classA;
    private long child1;

    @BeforeEach
    void setUp() {
        clock.now = BEFORE_CUTOFF;
        long campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, year);
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2026-09-01')",
                year);
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusA
                + ", " + year + ", 'Lớp A', " + ag3 + ") RETURNING id");
        child1 = child("An");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                child1,
                classA);
        parent = user("parent", CareNestRole.PARENT);
        otherParent = user("parent2", CareNestRole.PARENT);
        guardian(parent, child1);
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        teacherA = user("teachera", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacherA.userId(),
                classA);
    }

    @Test
    @DisplayName("P1: cả ngày ⇒ hủy mọi bữa, không hỏi ăn; ngày áp dụng bỏ thứ 7, chủ nhật")
    void fullDayPreviewCancelsAllMeals() {
        LeavePreviewResponse preview = service.previewLeaveRequest(parent, preview(THU, NEXT_MON, null, null));

        assertThat(preview.part()).isEqualTo(LeavePart.FULL_DAY);
        assertThat(preview.offMeals()).containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
        assertThat(preview.keptMeals()).isEmpty();
        assertThat(preview.eatsApplies()).isFalse();
        assertThat(preview.appliedDates()).containsExactly(THU, FRI, NEXT_MON);
        assertThat(preview.skippedDates()).isEmpty();
    }

    @Test
    @DisplayName("P2–P6: nửa buổi ⇒ bữa của buổi nghỉ bị hủy, bữa của buổi còn học được hỏi ăn")
    void halfDayPreviewSplitsMealsByDayPart() {
        LeavePreviewResponse morning = service.previewLeaveRequest(parent, preview(THU, THU, LeavePart.MORNING, false));
        assertThat(morning.offMeals()).containsExactly(MealSession.LUNCH);
        assertThat(morning.keptMeals()).containsExactly(MealSession.AFTERNOON);
        assertThat(morning.eatsApplies()).isTrue();

        LeavePreviewResponse afternoon =
                service.previewLeaveRequest(parent, preview(THU, THU, LeavePart.AFTERNOON, true));
        assertThat(afternoon.offMeals()).containsExactly(MealSession.AFTERNOON);
        assertThat(afternoon.keptMeals()).containsExactly(MealSession.LUNCH);
        assertThat(afternoon.eatsApplies()).isTrue();
    }

    @Test
    @DisplayName("P7, P8: nhóm tuổi không có Xế — nghỉ sáng không hỏi ăn, nghỉ chiều không hủy bữa nào")
    void groupWithoutAfternoonMeal() {
        jdbc.update(
                "UPDATE school_year_age_group_members SET meal_sessions='LUNCH' WHERE age_group_id=(SELECT id FROM age_groups WHERE code='ag-3')");
        jdbc.update("UPDATE age_groups SET meal_sessions = 'LUNCH' WHERE code = 'ag-3'");
        entityManager.clear();

        LeavePreviewResponse morning = service.previewLeaveRequest(parent, preview(THU, THU, LeavePart.MORNING, null));
        assertThat(morning.offMeals()).containsExactly(MealSession.LUNCH);
        assertThat(morning.keptMeals()).isEmpty();
        assertThat(morning.eatsApplies()).isFalse();

        LeavePreviewResponse afternoon =
                service.previewLeaveRequest(parent, preview(THU, THU, LeavePart.AFTERNOON, true));
        assertThat(afternoon.offMeals()).isEmpty();
        assertThat(afternoon.keptMeals()).containsExactly(MealSession.LUNCH);
        assertThat(afternoon.eatsApplies()).isTrue();
    }

    @Test
    @DisplayName("P10: sau giờ chốt hôm nay ⇒ hôm nay vào skippedDates, đơn áp dụng từ ngày học sau")
    void afterCutoffTodayIsSkipped() {
        clock.now = AFTER_CUTOFF;
        LeavePreviewResponse preview = service.previewLeaveRequest(parent, preview(TODAY, THU, null, null));
        assertThat(preview.skippedDates()).containsExactly(TODAY);
        assertThat(preview.appliedDates()).containsExactly(THU);
        // Đơn chỉ gồm hôm nay sau giờ chốt vẫn gửi được dạng thông báo (ATT-05b, chốt 22/11/2026)
        LeavePreviewResponse noticeOnly =
                service.previewLeaveRequest(parent, preview(TODAY, TODAY, LeavePart.MORNING, true));
        assertThat(noticeOnly.appliedDates()).isEmpty();
        assertThat(noticeOnly.skippedDates()).containsExactly(TODAY);
    }

    @Test
    @DisplayName("Xem trước báo đúng mã lỗi như gửi đơn (4019, 4020, 4021, 4022, 4025, 4026, 4028, 4031)")
    void previewRejectsLikeCreate() {
        expectCode(
                () -> service.previewLeaveRequest(parent, preview(FRI, THU, null, null)),
                ApiCode.LEAVE_DATE_RANGE_INVALID);
        expectCode(
                () -> service.previewLeaveRequest(parent, preview(TODAY.minusDays(1), TODAY, null, null)),
                ApiCode.LEAVE_DATE_IN_PAST);
        expectCode(
                () -> service.previewLeaveRequest(parent, preview(TODAY.plusDays(3), TODAY.plusDays(4), null, null)),
                ApiCode.LEAVE_NO_SCHOOL_DAY);
        expectCode(
                () -> service.previewLeaveRequest(otherParent, preview(THU, THU, null, null)),
                ApiCode.LEAVE_NOT_GUARDIAN);
        expectCode(
                () -> service.previewLeaveRequest(parent, preview(THU, TODAY.plusDays(40), null, null)),
                ApiCode.LEAVE_TOO_LONG);
        // P9: nửa buổi nhiều ngày
        expectCode(
                () -> service.previewLeaveRequest(parent, preview(THU, FRI, LeavePart.MORNING, true)),
                ApiCode.LEAVE_HALF_DAY_SINGLE_DAY);
        long noClass = child("Không lớp");
        guardian(parent, noClass);
        expectCode(
                () -> service.previewLeaveRequest(parent, new PreviewLeaveRequest(noClass, THU, THU, null, null, null)),
                ApiCode.LEAVE_CHILD_NO_CLASS);
        service.createLeaveRequest(parent, new CreateLeaveRequest(child1, FRI, FRI, "Ốm"));
        expectCode(() -> service.previewLeaveRequest(parent, preview(THU, FRI, null, null)), ApiCode.LEAVE_OVERLAP);
    }

    @Test
    @DisplayName("Xem trước không lưu gì: không đơn, không điểm danh, không thông báo")
    void previewSavesNothing() {
        long leaves = count("SELECT count(*) FROM leave_requests");
        long records = count("SELECT count(*) FROM attendance_records");
        long notices = count("SELECT count(*) FROM notifications");

        service.previewLeaveRequest(parent, preview(TODAY, NEXT_MON, null, null));
        service.previewLeaveRequest(parent, preview(TODAY, TODAY, LeavePart.MORNING, false));
        entityManager.flush();

        assertThat(count("SELECT count(*) FROM leave_requests")).isEqualTo(leaves);
        assertThat(count("SELECT count(*) FROM attendance_records")).isEqualTo(records);
        assertThat(count("SELECT count(*) FROM notifications")).isEqualTo(notices);
    }

    @Test
    @DisplayName("P11: danh sách đơn có bữa bị hủy; GV lớp mở danh sách hay màn điểm danh ⇒ ghi đã xem một lần")
    void leaveListShowsOffMealsAndTeacherSeenOnce() {
        long leaveId = service.createLeaveRequest(
                        parent, new CreateLeaveRequest(child1, THU, THU, "Đi khám", LeavePart.MORNING, true))
                .id();
        LeaveRequestResponse mine = only(service.findMyLeaveRequests(parent), leaveId);
        assertThat(mine.offMeals()).containsExactly(MealSession.LUNCH);
        assertThat(mine.eatsMeals()).containsExactly(MealSession.AFTERNOON);
        assertThat(mine.teacherSeenAt()).isNull();

        // BGH mở không tính
        service.searchLeaveRequests(principal, classA, null, TODAY, FRI, null);
        attendanceService.getClassAttendance(principal, classA, THU);
        entityManager.clear();
        assertThat(only(service.findMyLeaveRequests(parent), leaveId).teacherSeenAt())
                .isNull();

        service.searchLeaveRequests(teacherA, classA, null, TODAY, FRI, null);
        entityManager.clear();
        LeaveRequestResponse seen = only(service.findMyLeaveRequests(parent), leaveId);
        assertThat(seen.teacherSeenAt()).isEqualTo(BEFORE_CUTOFF);
        assertThat(seen.teacherSeenByName()).isEqualTo("teachera");

        // Người dạy thay mở sau: không ghi đè
        CurrentUser sub = user("sub", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_substitute) "
                        + "VALUES (?, 'TEACHER', ?, ?, ?, TRUE)",
                sub.userId(),
                classA,
                THU,
                THU);
        clock.now = BEFORE_CUTOFF.plusSeconds(60);
        attendanceService.getClassAttendance(sub, classA, THU);
        entityManager.clear();
        assertThat(only(service.findMyLeaveRequests(parent), leaveId).teacherSeenByName())
                .isEqualTo("teachera");
    }

    @Test
    @DisplayName("GV lớp mở màn điểm danh ngày của đơn ⇒ đã xem")
    void attendanceScreenMarksLeaveSeen() {
        long leaveId = service.createLeaveRequest(parent, new CreateLeaveRequest(child1, THU, FRI, "Ốm"))
                .id();
        attendanceService.getClassAttendance(teacherA, classA, TODAY);
        entityManager.clear();
        assertThat(only(service.findMyLeaveRequests(parent), leaveId).teacherSeenAt())
                .isNull();

        attendanceService.getClassAttendance(teacherA, classA, FRI);
        entityManager.clear();
        assertThat(only(service.findMyLeaveRequests(parent), leaveId).teacherSeenByName())
                .isEqualTo("teachera");
    }

    private static LeaveRequestResponse only(List<LeaveRequestResponse> leaves, long id) {
        return leaves.stream().filter(l -> l.id() == id).findFirst().orElseThrow();
    }

    private PreviewLeaveRequest preview(LocalDate from, LocalDate to, LeavePart part, Boolean eats) {
        return new PreviewLeaveRequest(child1, from, to, null, part, eats);
    }

    private long child(String name) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES"
                + " ('IT' || substr(md5(random()::text), 1, 12), '" + name
                + "', '2023-01-01', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
    }

    private void guardian(CurrentUser user, long childId) {
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, (SELECT count(*) FROM child_guardians WHERE child_id = ?), 'Mẹ (giả)',"
                        + " 'MOTHER', '0900000009', '000000000009', now())",
                user.userId(),
                childId,
                childId);
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions =
                switch (role) {
                    case PRINCIPAL -> Set.of(Permission.ATTENDANCE_VIEW, Permission.ATTENDANCE_UNLOCK);
                    case TEACHER -> Set.of(Permission.ATTENDANCE_VIEW, Permission.ATTENDANCE_RECORD);
                    default -> Set.of();
                };
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(apiCode);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
