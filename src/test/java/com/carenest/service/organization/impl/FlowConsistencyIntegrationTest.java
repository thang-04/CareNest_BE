package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.SchoolYearRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.SchoolYearService;
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

/** Vòng đời năm học trên PostgreSQL thật (V13: status, unique 1 năm ACTIVE, FK chặn xóa). Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class FlowConsistencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2027-08-30T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private SchoolYearService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long currentYear;

    @BeforeEach
    void setUp() {
        long principalId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-principal', 'principal.it', 'Principal IT') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        principal = new CurrentUser(
                principalId, "kc-principal", "principal.it", "Principal IT", null, Set.of(CareNestRole.PRINCIPAL));
        currentYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
    }

    @Autowired
    private com.carenest.service.observation.ClassMonthlyCommentService monthlyComments;

    @Test
    void shouldRejectShrinkingYearPastExistingHoliday() {
        var year = nextYear();
        service.replaceHolidays(
                principal,
                year,
                new com.carenest.dto.organization.SchoolHolidayDtos.SchoolHolidaysRequest(
                        List.of(new com.carenest.dto.organization.SchoolHolidayDtos.SchoolHolidayItem(
                                LocalDate.of(2028, 5, 22), LocalDate.of(2028, 5, 23), "Test holiday"))));
        assertThatThrownBy(() -> service.updateSchoolYear(
                        principal,
                        year,
                        new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 5), LocalDate.of(2028, 5, 15))))
                .as("existing holidays must remain within updated school-year bounds")
                .isInstanceOf(GlobalException.class);
    }

    @Test
    void deletingYearMustNotKeepItsHolidayInSchoolDayCache() {
        var year = nextYear();
        var day = LocalDate.of(2027, 9, 6);
        service.replaceHolidays(
                principal,
                year,
                new com.carenest.dto.organization.SchoolHolidayDtos.SchoolHolidaysRequest(
                        List.of(new com.carenest.dto.organization.SchoolHolidayDtos.SchoolHolidayItem(
                                day, day, "Test cached holiday"))));
        assertThat(service.isSchoolDay(day)).isFalse();
        service.deleteSchoolYear(principal, year);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM school_holidays WHERE school_year_id=?", Integer.class, year))
                .isZero();
        assertThat(service.isSchoolDay(day))
                .as("deleted holiday must no longer override weekday status")
                .isTrue();
    }

    @Test
    void monthlyClassCommentMustRejectMonthOutsideClassYear() {
        long campus = insertId("INSERT INTO campuses(code,name,is_main) VALUES ('DEEP','Deep',true) RETURNING id");
        long cls = insertId("INSERT INTO classrooms(campus_id,school_year_id,name) VALUES (" + campus + ","
                + currentYear + ",'Deep class') RETURNING id");
        long teacher = insertId(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name) VALUES ('deep-teacher','deep-teacher','Deep Teacher') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,classroom_id,valid_from) VALUES (?,'TEACHER',?,'2026-01-01')",
                teacher,
                cls);
        var user = new CurrentUser(
                teacher, "deep-teacher", "deep-teacher", "Deep Teacher", null, Set.of(CareNestRole.TEACHER));
        assertThatThrownBy(() -> monthlyComments.get(user, cls, LocalDate.of(2026, 8, 1)))
                .as("a class year starting September cannot create August class comments")
                .isInstanceOf(GlobalException.class);
        var invalid = new com.carenest.dto.observation.ClassMonthlyCommentDtos.ClassMonthlyCommentRequest(
                cls, LocalDate.of(2026, 8, 1), "An invalid outside-year monthly comment");
        assertThatThrownBy(() -> monthlyComments.save(user, invalid)).isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> monthlyComments.confirm(user, invalid)).isInstanceOf(GlobalException.class);
        jdbc.update("UPDATE school_years SET start_date='2026-09-05', end_date='2027-05-15' WHERE id=?", currentYear);
        assertThat(monthlyComments.get(user, cls, LocalDate.of(2026, 9, 30)).month())
                .isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(monthlyComments.get(user, cls, LocalDate.of(2027, 5, 1)).month())
                .isEqualTo(LocalDate.of(2027, 5, 1));
    }

    @Autowired
    private com.carenest.service.organization.YearSetupService yearSetup;

    @Test
    void changingYearDatesMovesYearBoundAssignments() {
        var year = nextYear();
        long campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-R', 'R', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long cls = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'Lớp R', " + ag3 + ") RETURNING id");
        long teacher = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-t-r', 't.r', 'GV R') RETURNING id");
        long teacherRow = insertId(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from," + " valid_to) VALUES ("
                        + teacher + ", 'TEACHER', " + cls + ", '2027-09-05', '2028-05-31') RETURNING id");
        long leaderRow = insertId("INSERT INTO staff_assignments (user_id, staff_role, age_group_id, school_year_id,"
                + " valid_from, valid_to) VALUES (" + teacher + ", 'AGE_GROUP_TEAM_LEADER', " + ag3 + ", " + year
                + ", '2027-09-05', '2028-05-31') RETURNING id");
        // GV nghỉ giữa năm: không bị kéo dài theo ngày cuối năm mới
        long leftRow = insertId(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from," + " valid_to) VALUES ("
                        + teacher + ", 'TEACHER', " + cls + ", '2027-09-05', '2027-12-31') RETURNING id");

        service.updateSchoolYear(
                principal,
                year,
                new SchoolYearRequest("2027-2028", LocalDate.of(2027, 9, 1), LocalDate.of(2028, 6, 15)));

        for (long id : new long[] {teacherRow, leaderRow}) {
            assertThat(jdbc.queryForObject(
                            "SELECT valid_from || '..' || valid_to FROM staff_assignments WHERE id = ?",
                            String.class,
                            id))
                    .isEqualTo("2027-09-01..2028-06-15");
        }
        assertThat(jdbc.queryForObject(
                        "SELECT valid_from || '..' || valid_to FROM staff_assignments WHERE id = ?",
                        String.class,
                        leftRow))
                .isEqualTo("2027-09-01..2027-12-31");
    }

    @Test
    void stoppedCampusDoesNotNeedViceprincipalForNewYear() {
        var year = nextYear();
        long active = insertId("INSERT INTO campuses (code, name, short_name, is_main) VALUES ('CS-V', 'V', 'V', TRUE)"
                + " RETURNING id");
        insertId("INSERT INTO campuses (code, name, short_name, is_main, status) VALUES ('CS-X', 'X', 'X', FALSE,"
                + " 'INACTIVE') RETURNING id");

        assertThat(yearSetup.getYearSetup(year).campusesWithoutVicePrincipal()).containsExactly(active);
    }

    private long nextYear() {
        return insertId(
                "INSERT INTO school_years(name,start_date,end_date,status) VALUES ('2027-2028','2027-09-05','2028-05-31','PLANNED') RETURNING id");
    }

    private String statusOf(long schoolYearId) {
        return jdbc.queryForObject("SELECT status FROM school_years WHERE id = ?", String.class, schoolYearId);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
