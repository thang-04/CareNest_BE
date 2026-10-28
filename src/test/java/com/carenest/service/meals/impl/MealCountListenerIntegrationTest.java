package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest.Entry;
import com.carenest.dto.meals.MealCountResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.attendance.AttendanceDayUnlockService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.meals.MealCountService;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Listener AttendanceChangedEvent chạy sau commit (ADR-0004) nên test không bọc @Transactional: mỗi lời gọi service
 * tự commit. Container riêng, chỉ một kịch bản: trẻ đến muộn sau khi suất đã xác nhận (ATT-07).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MealCountListenerIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final Instant AFTER_CUTOFF = Instant.parse("2026-10-21T02:00:00Z"); // 09:00 giờ trường

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(AFTER_CUTOFF, ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private MealCountService service;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceDayUnlockService unlockService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savingAttendanceAfterConfirmationMarksCountForReconfirmation() {
        long campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-L', 'L', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campus + ", " + year + ", 'Lớp L', " + ag3 + ") RETURNING id");
        long child1 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'An', '2023-01-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        long child2 = insertId(
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), 'Bình', '2023-02-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
        for (long child : List.of(child1, child2)) {
            jdbc.update(
                    "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                    child,
                    classroom);
        }
        CurrentUser vp = user("vpl", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vp.userId(),
                campus);
        CurrentUser teacher = user("teacherl", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacher.userId(),
                classroom);

        unlockService.unlockDay(
                vp, new DayUnlockRequest(classroom, TODAY, AFTER_CUTOFF.plus(Duration.ofHours(1)), "Quên lưu"));
        attendanceService.saveClassAttendance(teacher, classroom, request(present(child1)));
        MealCountResponse lunch = lunch(vp);
        service.confirm(vp, lunch.id());

        // Trẻ đến muộn: lưu lại trong lần mở lại ⇒ sau commit, sổ đã xác nhận chuyển chờ xác nhận lại
        attendanceService.saveClassAttendance(teacher, classroom, request(present(child1), present(child2)));

        MealCountResponse review = lunch(vp);
        assertThat(review.needsReconfirmation()).isTrue();
        assertThat(review.totals().normal()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM meal_count_history WHERE action = 'ATTENDANCE_CHANGED'", Long.class))
                .isPositive();
    }

    private MealCountResponse lunch(CurrentUser user) {
        return service.getMealCounts(user, TODAY, null).counts().stream()
                .filter(c -> c.session() == MealSession.LUNCH)
                .findFirst()
                .orElseThrow();
    }

    private static SaveAttendanceRequest request(Entry... entries) {
        return new SaveAttendanceRequest(TODAY, List.of(entries));
    }

    private static Entry present(long childId) {
        return new Entry(childId, AttendanceStatus.PRESENT, Map.of(MealSession.LUNCH, true));
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
