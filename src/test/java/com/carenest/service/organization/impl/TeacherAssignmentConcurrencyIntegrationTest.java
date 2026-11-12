package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.organization.ClassTeachersRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.TeacherAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** AUTH-24 trên PostgreSQL thật: hai yêu cầu đồng thời xếp cùng một GV vào hai lớp chỉ một yêu cầu thành công. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TeacherAssignmentConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private TeacherAssignmentService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sameTeacherAssignedToTwoClassesAtOnceOnlyOneSucceeds() throws Exception {
        long campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-C', 'C', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long class1 = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, capacity)"
                + " VALUES (" + campus + ", " + year + ", 'Lớp C1', " + ag3 + ", 25) RETURNING id");
        long class2 = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, capacity)"
                + " VALUES (" + campus + ", " + year + ", 'Lớp C2', " + ag3 + ", 25) RETURNING id");
        long principalId = user("principal-c");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        long keep1 = teacher("keep-c1", class1, null);
        long keep2 = teacher("keep-c2", class2, null);
        // GV đã dạy lớp C1 tới hôm qua: còn là giáo viên của điểm trường nhưng hiện chưa dạy lớp nào
        long shared = teacher("shared-c", class1, "2026-10-21");
        CurrentUser principal = new CurrentUser(
                principalId,
                "kc-principal-c",
                "principal-c",
                "P",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                Set.of(Permission.STAFF_ASSIGNMENT_MANAGE));

        var start = new CountDownLatch(1);
        var outcomes = new ConcurrentLinkedQueue<String>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = List.of(new long[] {class1, keep1}, new long[] {class2, keep2}).stream()
                    .<Future<?>>map(c -> pool.submit(() -> {
                        try {
                            start.await();
                            service.assignClassTeachers(
                                    principal, c[0], new ClassTeachersRequest(List.of(c[1], shared), c[1]));
                            outcomes.add("OK");
                        } catch (GlobalException ex) {
                            outcomes.add(ex.getApiCode().name());
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }))
                    .toList();
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "TEACHER_ALREADY_ASSIGNED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM staff_assignments WHERE user_id = ? AND staff_role = 'TEACHER'"
                                + " AND valid_from <= DATE '2026-10-22'"
                                + " AND (valid_to IS NULL OR valid_to >= DATE '2026-10-22')",
                        Long.class,
                        shared))
                .isEqualTo(1L);
    }

    private long teacher(String username, long classroomId, String validTo) {
        long id = user(username);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, valid_to) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', CAST(? AS date))",
                id,
                classroomId,
                validTo);
        return id;
    }

    private long user(String username) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
