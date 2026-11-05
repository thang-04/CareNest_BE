package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.observation.ClassAssessmentDetailResponse;
import com.carenest.dto.observation.TicketDtos.TicketSummaryRow;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.BehaviourTicketService;
import com.carenest.service.observation.ChildEvaluationService;
import com.carenest.service.observation.ClassAssessmentOverviewService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
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

/**
 * Trẻ chuyển lớp A → B ngày 15/10 và trẻ đã nghỉ: số liệu từng lớp chỉ lấy dữ liệu ghi ở lớp đó, trẻ đã rời vẫn còn
 * trong kỳ cũ (BUG-261102). Hôm nay = thứ 4, 21/10/2026.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class AssessmentHistoryIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final LocalDate MOVE = LocalDate.of(2026, 10, 15);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static final class FixedClock extends Clock {
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
            return Instant.parse("2026-10-21T02:00:00Z");
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        FixedClock fixedClock() {
            return new FixedClock();
        }
    }

    @Autowired
    private EvaluationSources sources;

    @Autowired
    private ClassroomService classroomService;

    @Autowired
    private ChildEvaluationService evaluationService;

    @Autowired
    private ClassAssessmentOverviewService overviewService;

    @Autowired
    private BehaviourTicketService ticketService;

    @Autowired
    private com.carenest.service.observation.QualityReportService qualityReportService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long campusA;
    private long classA;
    private long classB;
    private long recorder;
    private long mover;
    private long year;

    @BeforeEach
    void setUp() {
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusA
                + ", " + year + ", 'Lớp A', " + ag3 + ") RETURNING id");
        classB = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusA
                + ", " + year + ", 'Lớp B', " + ag3 + ") RETURNING id");
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        recorder = user("teachera", CareNestRole.TEACHER).userId();
        mover = child("Chuyển lớp");
        enroll(mover, classA, "2026-09-01", "2026-10-14");
        enroll(mover, classB, "2026-10-15", null);
        for (LocalDate d : schoolDays(LocalDate.of(2026, 10, 1), TODAY)) {
            record(mover, d.isBefore(MOVE) ? classA : classB, d);
        }
    }

    @Test
    void evaluationSourcesCountOnlyDaysInThatClass() {
        ClassroomInfoResponse a = classroomService.findClassroomInfo(classA).orElseThrow();
        ClassroomInfoResponse b = classroomService.findClassroomInfo(classB).orElseThrow();
        LocalDate from = LocalDate.of(2026, 10, 1);

        assertThat(sources.load(a, List.of(mover), from, TODAY, false)
                        .get(mover)
                        .presentDays())
                .isEqualTo(10);
        assertThat(sources.load(b, List.of(mover), from, TODAY, false)
                        .get(mover)
                        .presentDays())
                .isEqualTo(5);
    }

    @Test
    void developmentListKeepsChildWhoMovedAwayAsNotCurrent() {
        assertThat(evaluationService.developmentChildren(principal, null, classA))
                .extracting(r -> r.id(), r -> r.current())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(mover, false));
        assertThat(evaluationService.developmentChildren(principal, null, classB))
                .extracting(r -> r.id(), r -> r.current())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(mover, true));
    }

    @Test
    void oldClassTeacherSeesProfileOnlyUntilChildLeftTheClass() {
        CurrentUser oldTeacher = teacherOf("teacherold", classA);
        CurrentUser stranger = user("teacherx", CareNestRole.TEACHER);

        // Phiếu tháng của lớp mới không lộ cho GV lớp cũ
        jdbc.update(
                "INSERT INTO behaviour_tickets (child_id, classroom_id, campus_id, school_year_id, type, period_start, "
                        + "period_end, issued_by, issued_at) VALUES (?, ?, ?, ?, 'WEEKLY', '2026-10-19', '2026-10-23', ?, now())",
                mover,
                classB,
                campusA,
                year,
                recorder);

        var profile = evaluationService.development(oldTeacher, mover, LocalDate.of(2026, 10, 1), TODAY);
        assertThat(profile.tickets()).isEmpty();
        assertThat(evaluationService
                        .development(principal, mover, LocalDate.of(2026, 10, 1), TODAY)
                        .tickets())
                .hasSize(1);

        assertThat(profile.cls().id()).isEqualTo(classA);
        assertThat(profile.range().end()).isEqualTo(MOVE.minusDays(1));
        assertThat(profile.presentDays()).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> evaluationService.development(stranger, mover, LocalDate.of(2026, 10, 1), TODAY))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
    }

    @Test
    void developmentProfileOpensForChildWhoLeftInsideRange() {
        long left = child("Đã nghỉ");
        enroll(left, classA, "2026-10-05", "2026-10-16");
        record(left, classA, LocalDate.of(2026, 10, 6));

        var profile = evaluationService.development(principal, left, LocalDate.of(2026, 10, 1), TODAY);

        assertThat(profile.cls().id()).isEqualTo(classA);
        assertThat(profile.presentDays()).isEqualTo(1);
    }

    @Test
    void classOverviewKeepsTransferredChildWithOwnClassDaysOnly() {
        ClassAssessmentDetailResponse a = overviewService.getClassDetail(principal, classA, "MONTH", MOVE);
        ClassAssessmentDetailResponse b = overviewService.getClassDetail(principal, classB, "MONTH", MOVE);

        assertThat(a.children())
                .extracting(
                        ClassAssessmentDetailResponse.ChildRow::childId,
                        ClassAssessmentDetailResponse.ChildRow::presentDays)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(mover, 10));
        assertThat(b.children())
                .extracting(
                        ClassAssessmentDetailResponse.ChildRow::childId,
                        ClassAssessmentDetailResponse.ChildRow::presentDays)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(mover, 5));
    }

    @Test
    void ticketSummaryOfPastMonthKeepsChildWhoMovedAway() {
        assertThat(ticketService
                        .getSummary(principal, classA, LocalDate.of(2026, 10, 1))
                        .rows())
                .extracting(r -> r.child().id())
                .containsExactly(mover);
        assertThat(ticketService
                        .getSummary(principal, classA, LocalDate.of(2026, 9, 1))
                        .rows())
                .extracting((TicketSummaryRow r) -> r.child().id())
                .containsExactly(mover);
    }

    @Test
    void ticketOverviewTotalCountsTransferredChildOnce() {
        var overview = ticketService.getOverview(principal, null, LocalDate.of(2026, 10, 1), null);

        assertThat(overview.rows()).allSatisfy(r -> assertThat(r.children()).isEqualTo(1));
        assertThat(overview.totals().children()).isEqualTo(1);
    }

    @Test
    void qualityReportScopeForTeacherAndTeamLeader() {
        // Lớp B sang điểm trường khác để thử lọc điểm trường của GV / tổ trưởng
        long campusB = insertId("INSERT INTO campuses (code, name) VALUES ('CS-B', 'B') RETURNING id");
        jdbc.update("UPDATE classrooms SET campus_id = ? WHERE id = ?", campusB, classB);
        long ag4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        long classC = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campusA + ", " + year + ", 'Lớp C', " + ag4 + ") RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT age_group_id FROM classrooms WHERE id = ?", Long.class, classA);
        CurrentUser teacher = teacherOf("teacherqa", classA);
        CurrentUser leader = teacherOf("teamleader", classC);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) "
                        + "VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, '2026-09-01')",
                leader.userId(),
                ag3);
        java.time.YearMonth oct = java.time.YearMonth.of(2026, 10);

        // GV: chỉ lớp mình dạy, không có dòng tổng
        var own = qualityReportService.getReport(teacher, null, oct, null);
        assertThat(own.rows()).extracting(r -> r.classId()).containsExactly(classA);
        assertThat(own.totals()).isEmpty();
        assertThat(qualityReportService.getReport(teacher, null, oct, campusA).rows())
                .extracting(r -> r.classId())
                .containsExactly(classA);
        assertThatThrownBy(() -> qualityReportService.getReport(teacher, null, oct, campusB))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CAMPUS_ACCESS_DENIED);
        assertThat(qualityReportService.getReport(leader, null, oct, campusB).rows())
                .extracting(r -> r.classId())
                .containsExactly(classB);
        // Tổ trưởng nhóm tuổi của A, B: các lớp nhóm tuổi đó + lớp mình dạy
        assertThat(qualityReportService.getReport(leader, null, oct, null).rows())
                .extracting(r -> r.classId())
                .containsExactlyInAnyOrder(classA, classB, classC);
        // GV không có lớp: không thấy gì
        assertThat(qualityReportService
                        .getReport(user("teachernone", CareNestRole.TEACHER), null, oct, null)
                        .rows())
                .isEmpty();
    }

    @Test
    void qualityReportDefaultsToLastFinishedMonth() {
        // Hôm nay 21/10: tháng 10 còn dở ⇒ mặc định tháng 9
        assertThat(qualityReportService.getReport(principal, null, null, null).month())
                .isEqualTo("2026-09");
    }

    @Test
    void qualityReportUsesDaysChildBelongsToClassAndIssuedTickets() {
        long full = child("Cả tháng");
        long joined = child("Vào 15/10");
        enroll(full, classA, "2026-09-01", null);
        enroll(joined, classA, "2026-10-15", null);
        for (LocalDate d : schoolDays(LocalDate.of(2026, 10, 1), TODAY)) {
            record(full, classA, d);
            if (!d.isBefore(MOVE)) {
                record(joined, classA, d);
            }
        }
        // Bé ngoan theo phiếu GV đã phát (OBS-23): chỉ trẻ "Cả tháng" được phát phiếu tuần 05/10
        jdbc.update(
                "INSERT INTO behaviour_tickets (child_id, classroom_id, campus_id, school_year_id, type, period_start, "
                        + "period_end, issued_by, issued_at) VALUES (?, ?, ?, ?, 'WEEKLY', '2026-10-05', '2026-10-09', ?, now())",
                full,
                classA,
                campusA,
                year,
                recorder);

        var report = qualityReportService.getReport(principal, null, java.time.YearMonth.of(2026, 10), null);
        var a = report.rows().stream()
                .filter(r -> r.classId() == classA)
                .findFirst()
                .orElseThrow();
        var b = report.rows().stream()
                .filter(r -> r.classId() == classB)
                .findFirst()
                .orElseThrow();

        // 15 ngày học; trẻ vào 15/10 phải đi 5 ngày, trẻ chuyển đi phải đi 10 ngày ở lớp A
        assertThat(a.children()).isEqualTo(3);
        assertThat(a.possibleDays()).isEqualTo(30);
        assertThat(a.attendanceRate()).isEqualTo(100);
        assertThat(a.diligentChildren()).isEqualTo(3);
        assertThat(a.goodChildren()).isEqualTo(1);
        assertThat(a.allRoundChildren()).isZero();
        assertThat(b.diligentChildren()).isEqualTo(1);
        var school = report.totals().getLast();
        assertThat(school.level()).isEqualTo("TOAN_TRUONG");
        assertThat(school.children()).isEqualTo(3);
        assertThat(school.diligentChildren()).isEqualTo(3);
    }

    private static List<LocalDate> schoolDays(LocalDate from, LocalDate to) {
        return from.datesUntil(to.plusDays(1))
                .filter(d -> d.getDayOfWeek().getValue() <= 5)
                .toList();
    }

    // Hồ sơ trẻ bắt buộc mã, giới tính, trạng thái (tiếp nhận trẻ G94-180)
    private long child(String name) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ("
                + "'IT' || substr(md5(random()::text), 1, 12), '" + name
                + "', '2023-01-01', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
    }

    private void enroll(long childId, long classroomId, String from, String to) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, ?::date, ?::date)",
                childId,
                classroomId,
                from,
                to);
    }

    private void record(long childId, long classroomId, LocalDate date) {
        jdbc.update(
                "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, meals, recorded_by, recorded_at) "
                        + "VALUES (?, ?, ?, ?, 'PRESENT', 'LUNCH', ?, now())",
                childId,
                classroomId,
                campusA,
                date,
                recorder);
    }

    private CurrentUser teacherOf(String username, long classroomId) {
        CurrentUser teacher = user(username, CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacher.userId(),
                classroomId);
        return teacher;
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role));
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
