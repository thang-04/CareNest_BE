package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.attendance.AttendanceSummaryResponse;
import com.carenest.dto.attendance.AttendanceSummaryResponse.ChildTotal;
import com.carenest.dto.attendance.AttendanceSummaryResponse.DayRow;
import com.carenest.dto.attendance.LeaveRequestDtos.CreateLeaveRequest;
import com.carenest.dto.attendance.LeaveRequestDtos.LeaveDay;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.LeaveRequestService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.LeavePart;
import com.carenest.utils.enums.Permission;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.SessionFactory;
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

/** Lỗi tổng hợp điểm danh, đơn nghỉ khi chuyển lớp, quyền dạy thay. Hôm nay = thứ 4, 21/10/2026; giờ chốt 08:30. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class AttendanceRegressionIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final Instant BEFORE_CUTOFF = Instant.parse("2026-10-21T01:00:00Z");

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
    private AttendanceService service;

    @Autowired
    private LeaveRequestService leaveService;

    @Autowired
    private MovableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private CurrentUser principal;
    private CurrentUser parent;
    private CurrentUser teacherA;
    private long year;
    private long classA;
    private long classB;
    private long child1;
    private long child2;
    private long child3;

    @BeforeEach
    void setUp() {
        clock.now = BEFORE_CUTOFF;
        long campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status, semester1_start,"
                + " semester1_end, semester2_start, semester2_end) VALUES ('2026-2027', '2026-09-01', '2027-05-31',"
                + " 'ACTIVE', '2026-09-01', '2026-12-31', '2027-01-04', '2027-05-31') RETURNING id");
        com.carenest.support.YearAgeGroupFixture.apply(jdbc, year);
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2026-09-01')",
                year);
        classA = classroom(campusA, year, "Lớp A", ag3);
        classB = classroom(campusA, year, "Lớp B", ag3);
        child1 = child("An");
        child2 = child("Bình");
        child3 = child("Chi");
        enroll(child1, classA, "2026-09-01");
        enroll(child2, classA, "2026-09-01");
        enroll(child3, classB, "2026-09-01");
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        teacherA = user("teachera", CareNestRole.TEACHER);
        teach(teacherA, classA);
        parent = user("parent", CareNestRole.PARENT);
    }

    @Test
    @DisplayName("Tổng hợp chỉ tính ngày học trong năm học của lớp")
    void summaryClampsRangeToClassSchoolYear() {
        LocalDate from = LocalDate.of(2026, 8, 24);
        LocalDate to = LocalDate.of(2026, 9, 4);

        AttendanceSummaryResponse.ClassSummary summary = service.getClassSummary(teacherA, classA, from, to);

        assertThat(summary.days())
                .extracting(DayRow::date)
                .containsExactly(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 3),
                        LocalDate.of(2026, 9, 4));
        assertThat(summary.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(service.getSummary(principal, null, null, from, to).days()).hasSize(4);
    }

    @Test
    @DisplayName("Tổng hợp theo học kỳ lấy ngày học kỳ của năm học, học kỳ chưa tới hôm nay thì cắt ở hôm nay")
    void summaryBySemesterUsesSemesterDates() {
        AttendanceSummaryResponse.ClassSummary first = service.getClassSummary(principal, classA, null, null, 1);
        assertThat(first.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(first.to()).isEqualTo(TODAY);
        assertThat(service.getSummary(principal, null, null, null, null, null, 1)
                        .from())
                .isEqualTo(LocalDate.of(2026, 9, 1));
        // Học kỳ II chưa bắt đầu ⇒ chưa có ngày để tổng hợp
        expectCode(() -> service.getClassSummary(principal, classA, null, null, 2), ApiCode.INVALID_DATE_RANGE);
        expectCode(() -> service.getClassSummary(principal, classA, null, null, 3), ApiCode.SEMESTER_DATE_REQUIRED);
    }

    @Test
    @DisplayName("Bản ghi ngoài thời gian trẻ ở lớp (trẻ đã nghỉ còn Vắng có phép ngày sau) không được đếm")
    void summarySkipsRecordsOutsideChildStay() {
        LocalDate day = TODAY.minusDays(1);
        record(child2, classA, day, "EXCUSED");
        // Trẻ nghỉ học từ 20/10: lớp kết thúc 19/10, bản ghi 20/10 còn sót lại
        jdbc.update("UPDATE enrollments SET valid_to = ? WHERE child_id = ?", day.minusDays(1), child2);
        entityManager.clear();

        AttendanceSummaryResponse.ClassSummary summary =
                service.getClassSummary(principal, classA, day.minusDays(1), day);

        assertThat(summary.days().getLast().excused()).isZero();
        assertThat(totalOf(summary, child2).excused()).isZero();
    }

    @Test
    @DisplayName("Trẻ chuyển đến trong khoảng: ghi ngày chuyển đến và số liệu chỉ xem ở lớp cũ cùng năm")
    void summaryShowsTransferredInChildWithPreviousClassCounts() {
        LocalDate moved = TODAY.minusDays(1);
        record(child3, classB, TODAY.minusDays(2), "PRESENT");
        jdbc.update("UPDATE enrollments SET valid_to = ? WHERE child_id = ?", moved.minusDays(1), child3);
        enroll(child3, classA, moved.toString());
        record(child3, classA, moved, "PRESENT");
        entityManager.clear();

        AttendanceSummaryResponse.ClassSummary summary =
                service.getClassSummary(principal, classA, TODAY.minusDays(2), TODAY);

        ChildTotal row = totalOf(summary, child3);
        assertThat(row.present()).isEqualTo(1);
        assertThat(row.transferredInOn()).isEqualTo(moved);
        assertThat(row.previousClasses()).singleElement().satisfies(p -> {
            assertThat(p.classId()).isEqualTo(classB);
            assertThat(p.className()).isEqualTo("Lớp B");
            assertThat(p.from()).isEqualTo(TODAY.minusDays(2));
            assertThat(p.to()).isEqualTo(moved.minusDays(1));
            assertThat(p.present()).isEqualTo(1);
            assertThat(p.absent()).isZero();
        });
        assertThat(totalOf(summary, child1).transferredInOn()).isNull();
        assertThat(totalOf(summary, child1).previousClasses()).isEmpty();
    }

    @Test
    @DisplayName("Đơn nghỉ gửi trước khi chuyển lớp: ngày sau khi chuyển tính cho lớp mới (sổ báo ăn, tổng hợp, đơn)")
    void leaveDaysFollowClassOfEachDateAfterTransfer() {
        LocalDate fri = TODAY.plusDays(2);
        guardian(child1);
        long leaveId = leaveService
                .createLeaveRequest(parent, new CreateLeaveRequest(child1, fri, fri, "Ốm"))
                .id();
        transfer(child1, classB, fri);

        AttendanceService.ClassMealTally tallyB = tallyOf(fri, classB);
        assertThat(tallyB.absent()).isEqualTo(1);
        assertThat(tallyB.missing()).isEqualTo(1);
        assertThat(tallyOf(fri, classA).absent()).isZero();
        assertThat(leaveService.findMyLeaveRequests(parent))
                .filteredOn(r -> r.id().equals(leaveId))
                .singleElement()
                .satisfies(r -> assertThat(r.days())
                        .extracting(LeaveDay::date, LeaveDay::classId, LeaveDay::className)
                        .containsExactly(tuple(fri, classB, "Lớp B")));

        clock.now = Instant.parse("2026-10-23T03:00:00Z");
        assertThat(service.getClassSummary(principal, classB, fri, fri)
                        .days()
                        .getFirst()
                        .excused())
                .isEqualTo(1);
        assertThat(service.getClassSummary(principal, classA, fri, fri)
                        .days()
                        .getFirst()
                        .excused())
                .isZero();
    }

    @Test
    @DisplayName("Nghỉ sáng gửi trước khi chuyển lớp: GV lớp mới bấm Đã đến được")
    void morningLeaveArrivalWorksInNewClassAfterTransfer() {
        LocalDate fri = TODAY.plusDays(2);
        guardian(child1);
        leaveService.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, fri, fri, "Đi khám", LeavePart.MORNING, true));
        transfer(child1, classB, fri);
        CurrentUser teacherB = user("teacherb", CareNestRole.TEACHER);
        teach(teacherB, classB);
        clock.now = Instant.parse("2026-10-23T06:40:00Z"); // 13:40 thứ 6, trong khung nhận trẻ chiều

        service.markArrived(teacherB, classB, child1, fri);
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                        "SELECT classroom_id FROM attendance_records WHERE child_id = ? AND date = ?",
                        Long.class,
                        child1,
                        fri))
                .isEqualTo(classB);
    }

    @Test
    @DisplayName("Dạy thay đang trong đợt chỉ xem đúng các ngày dạy thay, GV dạy chính xem cả lớp")
    void activeSubstituteReadsOnlyOwnDates() {
        CurrentUser sub = user("subnow", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to, is_substitute) "
                        + "VALUES (?, 'TEACHER', ?, '2026-10-15', '2026-10-25', TRUE)",
                sub.userId(),
                classA);

        assertThat(service.getClassAttendance(sub, classA, LocalDate.of(2026, 10, 19)))
                .isNotNull();
        assertThat(service.getClassAttendance(sub, classA, TODAY).canRecord()).isTrue();
        expectCode(
                () -> service.getClassAttendance(sub, classA, LocalDate.of(2026, 10, 14)), ApiCode.CLASS_ACCESS_DENIED);
        expectCode(
                () -> service.getClassSummary(sub, classA, LocalDate.of(2026, 10, 1), TODAY),
                ApiCode.CLASS_ACCESS_DENIED);
        assertThat(service.getClassSummary(sub, classA, LocalDate.of(2026, 10, 15), TODAY))
                .isNotNull();
        assertThat(service.getClassAttendance(teacherA, classA, LocalDate.of(2026, 10, 14)))
                .isNotNull();
    }

    @Test
    @DisplayName("Danh sách lớp điểm danh của GV: mỗi lớp thêm không kéo theo nhiều truy vấn phân công")
    void classListDoesNotRepeatAssignmentLookupsPerClass() {
        long two = statements(() -> service.getClasses(teacherA, null));
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long campus = classCampus(classA);
        for (int i = 0; i < 5; i++) {
            classroom(campus, year, "Lớp thêm " + i, ag3);
        }
        long seven = statements(() -> service.getClasses(teacherA, null));
        assertThat((seven - two) / 5.0).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Danh sách đơn nghỉ: số truy vấn không tăng theo số đơn")
    void leaveListsDoNotQueryPerLeave() {
        guardian(child1);
        leaveService.createLeaveRequest(
                parent, new CreateLeaveRequest(child1, TODAY.plusDays(1), TODAY.plusDays(1), "Ốm"));
        long mineOne = statements(() -> leaveService.findMyLeaveRequests(parent));
        long searchOne = statements(
                () -> leaveService.searchLeaveRequests(teacherA, classA, null, TODAY, TODAY.plusDays(14), null));
        for (int i : new int[] {2, 5, 6}) {
            leaveService.createLeaveRequest(
                    parent, new CreateLeaveRequest(child1, TODAY.plusDays(i), TODAY.plusDays(i), "Ốm"));
        }
        assertThat(statements(() -> leaveService.findMyLeaveRequests(parent))).isEqualTo(mineOne);
        assertThat(statements(() ->
                        leaveService.searchLeaveRequests(teacherA, classA, null, TODAY, TODAY.plusDays(14), null)))
                .isEqualTo(searchOne);
    }

    @Test
    void resavingAttendanceKeepsHalfDayOfEarlyPickup() {
        // PCK-04: giao trước mốc tính đủ ngày (mặc định 15:00) ⇒ 0,5 ngày; GV lưu lại điểm danh không làm mất dấu
        service.saveClassAttendance(
                teacherA,
                classA,
                new com.carenest.dto.attendance.SaveAttendanceRequest(
                        TODAY,
                        java.util.List.of(
                                new com.carenest.dto.attendance.SaveAttendanceRequest.Entry(
                                        child1, com.carenest.utils.enums.AttendanceStatus.PRESENT, java.util.Map.of()),
                                new com.carenest.dto.attendance.SaveAttendanceRequest.Entry(
                                        child2,
                                        com.carenest.utils.enums.AttendanceStatus.PRESENT,
                                        java.util.Map.of()))));
        pickup(child1, "2026-10-21T07:45:00Z"); // 14:45 giờ trường
        pickup(child2, "2026-10-21T08:00:00Z"); // đúng 15:00 giờ trường ⇒ đủ ngày
        jdbc.update(
                "UPDATE attendance_records SET absent_part = 'AFTERNOON' WHERE child_id = ? AND date = ?",
                child1,
                TODAY);
        String mealsBefore = jdbc.queryForObject(
                "SELECT meals FROM attendance_records WHERE child_id = ? AND date = ?", String.class, child1, TODAY);
        entityManager.clear();

        service.saveClassAttendance(
                teacherA,
                classA,
                new com.carenest.dto.attendance.SaveAttendanceRequest(
                        TODAY,
                        java.util.List.of(
                                new com.carenest.dto.attendance.SaveAttendanceRequest.Entry(
                                        child1, com.carenest.utils.enums.AttendanceStatus.PRESENT, java.util.Map.of()),
                                new com.carenest.dto.attendance.SaveAttendanceRequest.Entry(
                                        child2,
                                        com.carenest.utils.enums.AttendanceStatus.PRESENT,
                                        java.util.Map.of()))));

        assertThat(jdbc.queryForObject(
                        "SELECT absent_part FROM attendance_records WHERE child_id = ? AND date = ?",
                        String.class,
                        child1,
                        TODAY))
                .isEqualTo("AFTERNOON");
        assertThat(jdbc.queryForObject(
                        "SELECT meals FROM attendance_records WHERE child_id = ? AND date = ?",
                        String.class,
                        child1,
                        TODAY))
                .isEqualTo(mealsBefore);
        assertThat(jdbc.queryForObject(
                        "SELECT absent_part FROM attendance_records WHERE child_id = ? AND date = ?",
                        String.class,
                        child2,
                        TODAY))
                .isNull();
    }

    private void pickup(long childId, String handedOverAt) {
        jdbc.update(
                "INSERT INTO pickup_records (child_id, classroom_id, campus_id, date, outcome, verification,"
                        + " handed_over_at, recorded_by, recorded_at) SELECT ?, id, campus_id, ?, 'HANDED_OVER',"
                        + " 'PHONE_CONFIRMED', ?::timestamptz, ?, now() FROM classrooms WHERE id = ?",
                childId,
                TODAY,
                handedOverAt,
                teacherA.userId(),
                classA);
    }

    private long statements(Supplier<?> call) {
        entityManager.flush();
        entityManager.clear();
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        call.get();
        return stats.getPrepareStatementCount();
    }

    private AttendanceService.ClassMealTally tallyOf(LocalDate date, long classId) {
        return service.tallyMeals(date, classCampus(classId), null).stream()
                .filter(t -> t.classroomId().equals(classId))
                .findFirst()
                .orElseThrow();
    }

    private void transfer(long childId, long toClass, LocalDate from) {
        jdbc.update(
                "UPDATE enrollments SET valid_to = ? WHERE child_id = ? AND valid_to IS NULL",
                from.minusDays(1),
                childId);
        enroll(childId, toClass, from.toString());
        entityManager.flush();
        entityManager.clear();
    }

    private void guardian(long childId) {
        jdbc.update(
                "INSERT INTO child_guardians (user_id, child_id, position, full_name, relationship, phone, citizen_id,"
                        + " linked_at) VALUES (?, ?, 0, 'Mẹ (giả)', 'MOTHER', '0900000009', '000000000009', now())",
                parent.userId(),
                childId);
    }

    private long classCampus(long classId) {
        return jdbc.queryForObject("SELECT campus_id FROM classrooms WHERE id = ?", Long.class, classId);
    }

    private static ChildTotal totalOf(AttendanceSummaryResponse.ClassSummary summary, long childId) {
        return summary.children().stream()
                .filter(c -> c.child().id() == childId)
                .findFirst()
                .orElseThrow();
    }

    private void record(long childId, long classroomId, LocalDate date, String status) {
        jdbc.update(
                "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, meals, recorded_by,"
                        + " recorded_at) SELECT ?, id, campus_id, ?, ?, '', ?, now() FROM classrooms WHERE id = ?",
                childId,
                date,
                status,
                principal.userId(),
                classroomId);
    }

    private long child(String name) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES"
                + " ('IT' || substr(md5(random()::text), 1, 12), '" + name
                + "', '2023-01-01', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions =
                switch (role) {
                    case PRINCIPAL, VICE_PRINCIPAL -> Set.of(Permission.ATTENDANCE_VIEW, Permission.ATTENDANCE_UNLOCK);
                    case TEACHER ->
                        Set.of(
                                Permission.ATTENDANCE_VIEW,
                                Permission.ATTENDANCE_RECORD,
                                Permission.DAILY_HEALTH_RECORD);
                    default -> Set.of();
                };
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
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

    private void enroll(long childId, long classroomId, String from) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, ?::date)",
                childId,
                classroomId,
                from);
    }

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
