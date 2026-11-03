package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.security.CurrentUser;
import com.carenest.service.observation.BehaviourTicketService;
import com.carenest.service.observation.ChildEvaluationService;
import com.carenest.service.observation.ClassAssessmentOverviewService;
import com.carenest.service.organization.ClassroomService;
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
class AssessmentContinuityIntegrationTest {

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
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long campusA;
    private long classA;
    private long classB;
    private long recorder;
    private long mover;

    @BeforeEach
    void setUp() {
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
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
    void receivingTeacherReadsConfirmedHistoryButCannotEditOrReadDraft() {
        var teacher = user("receiver", CareNestRole.TEACHER);
        assign(teacher.userId(), classB);
        long confirmed = evaluation(classA, true);
        long draft = evaluation(classB, false);
        var detail = evaluationService.get(teacher, confirmed);
        assertThat(detail.cls().teaches()).isFalse();
        assertThat(detail.cls().canEdit()).isFalse();
        assertThat(detail.evaluation().aiDraft()).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> evaluationService.saveContent(teacher, confirmed, "Must not modify history"))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        jdbc.update(
                "UPDATE child_evaluations SET classroom_id = ?, period_start = '2026-09-01', period_end = '2026-09-30' WHERE id = ?",
                classA,
                draft);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> evaluationService.get(teacher, draft))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        jdbc.update("UPDATE staff_assignments SET valid_to = '2026-10-20' WHERE user_id = ?", teacher.userId());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> evaluationService.get(teacher, confirmed))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
    }

    @Test
    void unrelatedTeacherCannotReadConfirmedHistory() {
        var outsider = user("outsider", CareNestRole.TEACHER);
        long confirmed = evaluation(classA, true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> evaluationService.get(outsider, confirmed))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
    }

    @Test
    void sameChildEvaluationRowsKeepClassFlagsSeparate() {
        flag(classA, "2026-10-12");
        flag(classA, "2026-10-13");
        flag(classB, "2026-10-15");
        evaluation(classA, true);
        evaluation(classB, true);
        var rows = evaluationService.list(
                principal, com.carenest.utils.enums.EvaluationPeriodType.MONTH, null, null, LocalDate.of(2026, 10, 1));
        assertThat(rows.stream()
                        .filter(r -> r.classId().equals(classA))
                        .findFirst()
                        .orElseThrow()
                        .flagCount())
                .isEqualTo(2);
        assertThat(rows.stream()
                        .filter(r -> r.classId().equals(classB))
                        .findFirst()
                        .orElseThrow()
                        .flagCount())
                .isEqualTo(1);
    }

    @Test
    void weeklyEvidenceFollowsChildButSummaryFlagsStayInClassAndTicketIsNotDuplicated() {
        flag(classA, "2026-10-12");
        flag(classA, "2026-10-13");
        flag(classB, "2026-10-15");
        var period = LocalDate.of(2026, 10, 12);
        var type = com.carenest.utils.enums.TicketType.WEEKLY;
        var boardA = ticketService.getBoard(principal, classA, type, period);
        assertThat(boardA.rows()).hasSize(1);
        assertThat(boardA.rows().getFirst().canIssue()).isFalse();
        var oldTeacher = user("former-ticket-teacher", CareNestRole.TEACHER);
        assign(oldTeacher.userId(), classA);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ticketService.issue(
                        oldTeacher,
                        new com.carenest.dto.observation.TicketDtos.IssueTicketsRequest(
                                classA, type, period, List.of(mover))))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        assertThat(ticketService
                        .getSummary(principal, classA, LocalDate.of(2026, 10, 1))
                        .rows()
                        .getFirst()
                        .flags())
                .isEqualTo(2);
        var teacher = user("ticket-receiver", CareNestRole.TEACHER);
        assign(teacher.userId(), classB);
        var before =
                ticketService.getBoard(teacher, classB, type, period).rows().getFirst();
        assertThat(before.presentDays()).isEqualTo(5);
        assertThat(before.flagCount()).isEqualTo(3);
        var request =
                new com.carenest.dto.observation.TicketDtos.IssueTicketsRequest(classB, type, period, List.of(mover));
        ticketService.issue(teacher, request);
        var oldBoard =
                ticketService.getBoard(principal, classA, type, period).rows().getFirst();
        assertThat(oldBoard.ticket()).isNotNull();
        assertThat(oldBoard.issuedElsewhere()).isTrue();
        assertThat(oldBoard.eligible()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ticketService.issue(teacher, request))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM behaviour_tickets WHERE child_id = ?", Integer.class, mover))
                .isEqualTo(1);
    }

    private void assign(long userId, long classId) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) VALUES (?, 'TEACHER', ?, '2026-09-01', false)",
                userId,
                classId);
    }

    private void flag(long classId, String day) {
        jdbc.update(
                "INSERT INTO daily_observations (child_id, classroom_id, campus_id, date, meals, behaviors, flag, recorded_by, recorded_at) VALUES (?, ?, ?, ?::date, '{}', '', true, ?, now())",
                mover,
                classId,
                campusA,
                day,
                recorder);
    }

    private long evaluation(long classId, boolean confirmed) {
        long year = jdbc.queryForObject("SELECT school_year_id FROM classrooms WHERE id = ?", Long.class, classId);
        return jdbc.queryForObject(
                "INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id, period_type, period_start, period_end, status, draft_content, final_content, confirmed_by, confirmed_at, created_at, updated_at) VALUES (?, ?, ?, ?, 'MONTH', '2026-10-01', '2026-10-31', ?, 'Private draft', ?, ?, ?, now(), now()) RETURNING id",
                Long.class,
                mover,
                classId,
                campusA,
                year,
                confirmed ? "CONFIRMED" : "AWAITING_REVIEW",
                confirmed ? "Confirmed historical evaluation" : null,
                confirmed ? recorder : null,
                confirmed ? java.sql.Timestamp.from(Instant.parse("2026-10-21T01:00:00Z")) : null);
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

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role));
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
