package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.organization.VicePrincipalAssignmentRequest;
import com.carenest.dto.organization.VicePrincipalAssignmentResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.VicePrincipalAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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

/** Phân công PHT vào điểm trường theo năm học trên PostgreSQL thật. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class VicePrincipalAssignmentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private VicePrincipalAssignmentService service;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private long vp1;
    private long vp2;
    private long teacher;
    private long campusA;
    private long campusB;
    private long campusC;
    private long activeYear;
    private long plannedYear;

    @BeforeEach
    void setUp() {
        long principalId = user("principal");
        vp1 = user("vp1");
        vp2 = user("vp2");
        teacher = user("teacher");
        campusA = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'Campus A', TRUE) RETURNING id");
        campusB = insertId("INSERT INTO campuses (code, name) VALUES ('CS-B', 'Campus B') RETURNING id");
        campusC = insertId("INSERT INTO campuses (code, name) VALUES ('CS-C', 'Campus C') RETURNING id");
        activeYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        plannedYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2027-2028', '2027-08-01', '2028-05-31', 'PLANNED') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vp1,
                campusA);
        // vp2 từng phụ trách B ở năm trước, năm nay chưa có
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from, valid_to) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2025-09-01', '2026-05-31')",
                vp2,
                campusB);
        principal =
                new CurrentUser(principalId, "kc-principal", "principal", "P", null, Set.of(CareNestRole.PRINCIPAL));
    }

    @Test
    void listsVicePrincipalsWithTheirCampusForTheYear() {
        Map<Long, Long> campuses = campusByUser(service.getAssignments(principal, activeYear));

        assertThat(campuses).containsOnlyKeys(vp1, vp2);
        assertThat(campuses.get(vp1)).isEqualTo(campusA);
        assertThat(campuses.get(vp2)).isNull();
    }

    @Test
    void plannedYearAssignmentStartsWithTheYearAndKeepsTheCurrentOne() {
        Map<Long, Long> planned =
                campusByUser(service.assign(principal, plannedYear, vp1, new VicePrincipalAssignmentRequest(campusB)));

        assertThat(planned.get(vp1)).isEqualTo(campusB);
        assertThat(campusByUser(service.getAssignments(principal, activeYear)).get(vp1))
                .isEqualTo(campusA);
        assertThat(validTo(vp1, campusA)).isEqualTo(LocalDate.of(2027, 7, 31));
    }

    @Test
    void changingTheActiveYearEndsTheOldCampusYesterdayAndKeepsNextYear() {
        service.assign(principal, plannedYear, vp1, new VicePrincipalAssignmentRequest(campusB));

        service.assign(principal, activeYear, vp1, new VicePrincipalAssignmentRequest(campusC));

        assertThat(validTo(vp1, campusA)).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(validTo(vp1, campusC)).isEqualTo(LocalDate.of(2027, 7, 31));
        assertThat(campusByUser(service.getAssignments(principal, plannedYear)).get(vp1))
                .isEqualTo(campusB);
    }

    @Test
    void sameCampusIsUnchangedAndNullRemoves() {
        service.assign(principal, activeYear, vp1, new VicePrincipalAssignmentRequest(campusA));
        assertThat(count(vp1)).isEqualTo(1);

        Map<Long, Long> campuses =
                campusByUser(service.assign(principal, activeYear, vp1, new VicePrincipalAssignmentRequest(null)));

        assertThat(campuses.get(vp1)).isNull();
        assertThat(validTo(vp1, campusA)).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    void rejectsNonVicePrincipalInactiveCampusAndClosedYear() {
        assertCode(
                () -> service.assign(principal, activeYear, teacher, new VicePrincipalAssignmentRequest(campusA)),
                ApiCode.VICE_PRINCIPAL_INVALID);
        jdbc.update("UPDATE campuses SET status = 'INACTIVE' WHERE id = ?", campusC);
        assertCode(
                () -> service.assign(principal, activeYear, vp2, new VicePrincipalAssignmentRequest(campusC)),
                ApiCode.CAMPUS_INACTIVE);
        // Năm đã kết thúc chỉ đọc
        long closedYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2025-2026', '2025-09-01', '2026-05-31', 'CLOSED') RETURNING id");
        assertCode(
                () -> service.assign(principal, closedYear, vp2, new VicePrincipalAssignmentRequest(campusA)),
                ApiCode.SCHOOL_YEAR_CLOSED);
    }

    @Test
    void vicePrincipalCannotAssignVicePrincipalsEvenInOwnCampus() {
        CurrentUser vice = new CurrentUser(vp1, "kc-vp1", "vp1", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL));

        // AUTH-16: PHT chỉ phân công GV/bếp trong campus mình; phân công PHT do HT
        assertCode(() -> service.getAssignments(vice, activeYear), ApiCode.STAFF_ASSIGNMENT_MANAGE_DENIED);
        assertCode(
                () -> service.assign(vice, activeYear, vp2, new VicePrincipalAssignmentRequest(campusA)),
                ApiCode.STAFF_ASSIGNMENT_MANAGE_DENIED);
        assertThat(count(vp2)).isEqualTo(1);
    }

    private Map<Long, Long> campusByUser(VicePrincipalAssignmentResponse response) {
        Map<Long, Long> out = new java.util.HashMap<>();
        response.rows()
                .forEach(r -> out.put(
                        r.user().id(),
                        r.assignment() == null ? null : r.assignment().campusId()));
        return out;
    }

    private LocalDate validTo(long userId, long campusId) {
        List<LocalDate> rows = jdbc.queryForList(
                "SELECT valid_to FROM staff_assignments WHERE user_id = ? AND campus_id = ? ORDER BY id",
                LocalDate.class,
                userId,
                campusId);
        return rows.get(rows.size() - 1);
    }

    private int count(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM staff_assignments WHERE user_id = ?", Integer.class, userId);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(code);
    }

    private long user(String username) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + " IT') RETURNING id");
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
