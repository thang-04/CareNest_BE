package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.child.SavePlacementsRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.YearTransitionService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.PlacementAction;
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

/**
 * Lưu xếp lớp năm mới đồng thời (PostgreSQL thật, mỗi luồng một giao dịch): hai người cùng xếp hai trẻ khác nhau vào
 * một lớp chỉ còn 1 chỗ thì chỉ một người thành công, lớp không vượt sĩ số. Hôm nay = 30/08/2027.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class YearTransitionConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2027-08-30T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private YearTransitionService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoPlacementsIntoTheLastSeatOfAClassOnlyOneSucceeds() throws Exception {
        long principalId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-cc-principal', 'cc-principal', 'cc-principal') RETURNING id");
        long campus =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-CC', 'Campus CC', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long oldYear = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long newYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2027-2028', '2027-09-05', '2028-05-31') RETURNING id");
        long room1 = room(campus, "CC.1");
        long room2 = room(campus, "CC.2");
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address, phone, status) "
                        + "SELECT y.id, c.id, c.code, c.name, c.short_name, c.address, c.phone, c.status "
                        + "FROM school_years y CROSS JOIN campuses c WHERE y.id IN (?, ?)",
                oldYear,
                newYear);
        jdbc.update(
                "INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note) "
                        + "SELECT y.id, g.id, g.name, g.short_name, g.age_from_months, g.age_to_months, g.meal_sessions, g.max_class_size, g.nutrition_note "
                        + "FROM school_years y CROSS JOIN age_groups g WHERE y.id IN (?, ?)",
                oldYear,
                newYear);
        jdbc.update(
                "INSERT INTO school_year_room_members (school_year_id, room_id, campus_id, code, name, room_type, capacity, manager_user_id, status, note) "
                        + "SELECT y.id, r.id, r.campus_id, r.code, r.name, r.room_type, r.capacity, r.manager_user_id, r.status, r.note "
                        + "FROM school_years y CROSS JOIN rooms r WHERE y.id IN (?, ?)",
                oldYear,
                newYear);
        long oldClass = classroom(campus, oldYear, "Lớp cũ CC", ag3, room1, 25);
        long newClass = classroom(campus, newYear, "Lớp bé CC", ag3, room2, 1);
        long child1 = child("Trẻ CC 1", "2023-01-01");
        long child2 = child("Trẻ CC 2", "2023-02-01");
        for (long child : List.of(child1, child2)) {
            jdbc.update(
                    "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                    child,
                    oldClass);
        }
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        CurrentUser principal = new CurrentUser(
                principalId, "kc-cc-principal", "cc-principal", "P", null, Set.of(CareNestRole.PRINCIPAL));

        var start = new CountDownLatch(1);
        var outcomes = new ConcurrentLinkedQueue<String>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = List.of(child1, child2).stream()
                    .<Future<?>>map(child -> pool.submit(() -> {
                        try {
                            start.await();
                            service.savePlacements(
                                    principal,
                                    newYear,
                                    new SavePlacementsRequest(List.of(
                                            new SavePlacementsRequest.Item(child, PlacementAction.STAY, newClass))));
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

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "PLACEMENT_CLASS_FULL");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM class_placements WHERE to_classroom_id = ?", Long.class, newClass))
                .isEqualTo(1L);
    }

    private long child(String name, String dateOfBirth) {
        return insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('CC' || substr(md5(random()::text), 1, 12), '"
                        + name + "', '" + dateOfBirth + "', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
    }

    private long room(long campusId, String code) {
        return insertId("INSERT INTO rooms (campus_id, code, name, room_type, capacity) VALUES (" + campusId + ", '"
                + code + "', 'Phòng " + code + "', 'CLASS', 30) RETURNING id");
    }

    private long classroom(long campusId, long yearId, String name, long ageGroupId, long roomId, int capacity) {
        return insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id, room_id, capacity) "
                + "VALUES (" + campusId + ", " + yearId + ", '" + name + "', " + ageGroupId + ", " + roomId + ", "
                + capacity + ") RETURNING id");
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
