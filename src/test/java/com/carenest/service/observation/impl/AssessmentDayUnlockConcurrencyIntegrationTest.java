package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.observation.AssessmentDayUnlockRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.AssessmentDayUnlockService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

/** OBS-19 trên PostgreSQL thật: HT và PHT mở lại cùng một ngày đánh giá cùng lúc chỉ tạo một lần mở. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AssessmentDayUnlockConcurrencyIntegrationTest {

    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 27);
    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private AssessmentDayUnlockService unlockService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void simultaneousUnlocksOfSameDayCreateOnlyOne() throws Exception {
        long campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-U', 'U', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long classId = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campus + ", " + year + ", 'Lớp U', " + ag3 + ") RETURNING id");
        CurrentUser principal = user("principal-u", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        CurrentUser vp = user("vp-u", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vp.userId(),
                campus);
        var request =
                new AssessmentDayUnlockRequest(classId, YESTERDAY, NOW.plusSeconds(3600), "GV gửi mail xin mở lại");

        var start = new CountDownLatch(1);
        var outcomes = new ConcurrentLinkedQueue<String>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = List.of(principal, vp).stream()
                    .<Future<?>>map(u -> pool.submit(() -> {
                        try {
                            start.await();
                            unlockService.unlockDay(u, request);
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

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "ASSESSMENT_UNLOCK_ALREADY_ACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM assessment_day_unlocks WHERE classroom_id = ?", Long.class, classId))
                .isEqualTo(1L);
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        return new CurrentUser(
                id, "kc-" + username, username, username, null, Set.of(role), Set.of(Permission.ASSESSMENT_UNLOCK));
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
