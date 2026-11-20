package com.carenest.service.attendance.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.attendance.AttendanceSummaryResponse;
import com.carenest.dto.attendance.DailyHealthRequest;
import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceDayUnlockService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.DailyHealthService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthObservation;
import com.carenest.utils.enums.Permission;
import java.math.BigDecimal;
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

/** Tổng hợp theo sĩ số từng ngày và sức khỏe ngày mở lại (BUG-261103). Hôm nay = thứ 4, 21/10/2026, 09:00. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class AttendanceHistoryIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

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
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceDayUnlockService unlockService;

    @Autowired
    private DailyHealthService healthService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vpA;
    private CurrentUser teacherA;
    private long campusA;
    private long classA;

    @BeforeEach
    void setUp() {
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'A', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        jdbc.update(
                "INSERT INTO attendance_cutoffs (school_year_id, cutoff_time, effective_from) VALUES (?, '08:30', '2026-09-01')",
                year);
        classA = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campusA
                + ", " + year + ", 'Lớp A', " + ag3 + ") RETURNING id");
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vpA = user("vpa", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vpA.userId(),
                campusA);
        teacherA = user("teachera", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacherA.userId(),
                classA);
    }

    @Test
    void summaryCountsEachDayWithChildrenEnrolledThatDay() {
        long stays = child("Ở lại");
        long left = child("Đã nghỉ");
        long joined = child("Mới vào");
        enroll(stays, "2026-09-01", null);
        enroll(left, "2026-09-01", "2026-10-19");
        enroll(joined, "2026-10-21", null);
        record(stays, "2026-10-19");
        record(left, "2026-10-19");

        AttendanceSummaryResponse.ClassSummary s =
                attendanceService.getClassSummary(principal, classA, LocalDate.of(2026, 10, 19), TODAY);

        var day19 = s.days().getFirst();
        assertThat(day19.present()).isEqualTo(2);
        assertThat(day19.missing()).isZero();
        // 20/10: trẻ đã nghỉ và trẻ chưa vào lớp không bị tính "chưa điểm danh"
        assertThat(s.days().get(1).missing()).isEqualTo(1);
        assertThat(s.days().getLast().missing()).isEqualTo(2);
        assertThat(s.children())
                .extracting(c -> c.child().id(), AttendanceSummaryResponse.ChildTotal::present)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(stays, 1),
                        org.assertj.core.groups.Tuple.tuple(left, 1),
                        org.assertj.core.groups.Tuple.tuple(joined, 0));
    }

    @Test
    void healthCanBeRecordedOnReopenedDay() {
        long kid = child("Bé ốm");
        enroll(kid, "2026-09-01", null);
        LocalDate yesterday = TODAY.minusDays(1);
        record(kid, yesterday.toString());
        unlockService.unlockDay(
                vpA, new DayUnlockRequest(classA, yesterday, Instant.parse("2026-10-21T05:00:00Z"), "bổ sung"));

        var entry = healthService.addEntry(
                teacherA,
                new DailyHealthRequest(
                        "rv-1",
                        classA,
                        kid,
                        yesterday,
                        new BigDecimal("36.8"),
                        HealthObservation.NO_CONCERN,
                        List.of(),
                        "",
                        List.of(),
                        null));

        assertThat(entry.id()).isNotNull();
    }

    // Hồ sơ trẻ bắt buộc mã, giới tính, trạng thái (tiếp nhận trẻ G94-180)
    private long child(String name) {
        return insertId("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ("
                + "'IT' || substr(md5(random()::text), 1, 12), '" + name
                + "', '2023-01-01', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
    }

    private void enroll(long childId, String from, String to) {
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, ?::date, ?::date)",
                childId,
                classA,
                from,
                to);
    }

    private void record(long childId, String date) {
        jdbc.update(
                "INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, meals, recorded_by, recorded_at) "
                        + "VALUES (?, ?, ?, ?::date, 'PRESENT', 'LUNCH', ?, now())",
                childId,
                classA,
                campusA,
                date,
                teacherA.userId());
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        Set<Permission> permissions = role == CareNestRole.TEACHER
                ? Set.of(Permission.ATTENDANCE_VIEW, Permission.DAILY_HEALTH_RECORD)
                : Set.of(Permission.ATTENDANCE_VIEW, Permission.ATTENDANCE_UNLOCK);
        return new CurrentUser(id, "kc-" + username, username, username, null, Set.of(role), permissions);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
