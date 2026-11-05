package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.entity.meals.MealCount;
import com.carenest.service.meals.MealCountService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.StaffRole;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MealCountConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Autowired
    PlatformTransactionManager manager;

    @Autowired
    MealCountService service;

    @Autowired
    ClassroomService classes;

    @Test
    void concurrentClassChangesLockCampusBeforeReadingMealState() throws Exception {
        long campus = id("INSERT INTO campuses(code,name,is_main) VALUES ('RACE','Race',true) RETURNING id");
        long year = id(
                "INSERT INTO school_years(name,start_date,end_date,status) VALUES ('2026-2027','2026-09-01','2027-05-31','ACTIVE') RETURNING id");
        long ag = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-3'", Long.class);
        long recorder = id(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name) VALUES ('race','race','Race') RETURNING id");
        long a = classroom(campus, year, ag, "A"), b = classroom(campus, year, ag, "B");
        var date = LocalDate.of(2026, 10, 21);
        for (long cls : new long[] {a, b}) {
            long child =
                    id("INSERT INTO children(code,full_name,date_of_birth,gender,status,enrolled_on) VALUES ('RACE-"
                            + cls + "','TEST Race','2023-01-01','MALE','ACTIVE','2026-09-01') RETURNING id");
            jdbc.update(
                    "INSERT INTO enrollments(child_id,classroom_id,valid_from) VALUES (?,?,'2026-09-01')", child, cls);
            jdbc.update(
                    "INSERT INTO attendance_records(child_id,classroom_id,campus_id,date,status,meals,recorded_by,recorded_at) VALUES (?,?,?,?,'PRESENT','LUNCH',?,now())",
                    child,
                    cls,
                    campus,
                    date,
                    recorder);
        }
        var tx = new TransactionTemplate(manager);
        long countId = tx.execute(s -> {
            var c = MealCount.aggregate(campus, year, date, MealSession.LUNCH, Instant.now());
            c.addLine(a, 2, 0, 0, 2, 0);
            c.addLine(b, 2, 0, 0, 2, 0);
            c.confirm(recorder, StaffRole.PRINCIPAL, Instant.now());
            em.persist(c);
            em.flush();
            return c.getId();
        });
        var loaded = new CountDownLatch(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<String>>();
            for (long cls : new long[] {a, b})
                futures.add(pool.submit(() -> {
                    try {
                        tx.execute(s -> {
                            loaded.countDown();
                            try {
                                if (!loaded.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("barrier");
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            service.markAttendanceChanged(date, campus, cls);
                            return null;
                        });
                        return "";
                    } catch (RuntimeException e) {
                        return e.getClass().getSimpleName() + ":" + e.getMessage();
                    }
                }));
            var failures = new ArrayList<String>();
            for (var future : futures) {
                var error = future.get(30, TimeUnit.SECONDS);
                if (!error.isEmpty()) failures.add(error);
            }
            assertThat(failures)
                    .as("both class updates must commit without campus pending-line collision")
                    .isEmpty();
            assertThat(jdbc.queryForList(
                            "SELECT normal FROM meal_count_lines WHERE meal_count_id=? AND pending ORDER BY classroom_id",
                            Integer.class,
                            countId))
                    .containsExactly(1, 1);
        } finally {
            pool.shutdownNow();
        }
    }

    private long classroom(long campus, long year, long ag, String name) {
        return id("INSERT INTO classrooms(campus_id,school_year_id,age_group_id,name) VALUES (" + campus + "," + year
                + "," + ag + ",'" + name + "') RETURNING id");
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
