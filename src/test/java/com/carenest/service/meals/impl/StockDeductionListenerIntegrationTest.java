package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest.Entry;
import com.carenest.dto.meals.MealCountResponse;
import com.carenest.dto.meals.StockReceiptRequest;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.attendance.AttendanceDayUnlockService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.meals.MealCountService;
import com.carenest.service.meals.StockReceiptService;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
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
 * Trừ kho chạy sau commit khi suất được xác nhận (ADR-0004, P-38) nên test không bọc @Transactional. Một kịch bản:
 * tuần đã công bố, kho có gạo, PHT xác nhận suất ⇒ kho bị trừ; trẻ đến muộn, xác nhận lại ⇒ chỉ ghi chênh lệch.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class StockDeductionListenerIntegrationTest {

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
    private MealCountService mealCountService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceDayUnlockService unlockService;

    @Autowired
    private StockReceiptService receiptService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("P-38, ATT-07: xác nhận suất ⇒ trừ kho sau commit; xác nhận lại ⇒ chỉ ghi điều chỉnh chênh lệch")
    void confirmingMealCountDeductsDryStockAfterCommit() {
        long campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-E', 'E', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campus + ", " + year + ", 'Lớp E', " + ag3 + ") RETURNING id");
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
        // Thực đơn tuần 19/10 đã công bố: trưa cơm 60 g gạo/trẻ (kho khô, không thải bỏ)
        long rice = insertId("INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size,"
                + " unit_price, kcal, protein, lipid, glucid, nutrient_source, storage, origin)"
                + " VALUES ('GAO-E', 'Gạo', 'GRAIN', 'g', 'kg', 1000, 20000, 350, 7, 1, 77, 'TEST', 'DRY', 'PLANT')"
                + " RETURNING id");
        long dish = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('COM-E', 'Cơm', 'CARB') RETURNING id");
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, 60)",
                dish,
                rice,
                ag3);
        long week = insertId("INSERT INTO meal_plan_weeks (week_start, status, published_at)"
                + " VALUES ('2026-10-19', 'PUBLISHED', now()) RETURNING id");
        LocalDate day = LocalDate.of(2026, 10, 21);
        jdbc.update(
                "UPDATE meal_plan_weeks SET days = days || jsonb_build_array("
                        + "jsonb_build_object('date', ?::date, 'off', false, 'note', '')) WHERE id = ?",
                day,
                week);
        jdbc.update(
                "INSERT INTO meal_plan_items (week_id, menu_date, session, menu_block, dish_id)"
                        + " VALUES (?, ?, 'LUNCH', 'KINDERGARTEN', ?)",
                week,
                day,
                dish);

        CurrentUser principal = user("principal-e", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        long supplier = insertId("INSERT INTO suppliers (name, phone) VALUES ('NCC E', '0900000002') RETURNING id");
        var receipt = receiptService.createReceipt(
                principal,
                new StockReceiptRequest(
                        campus,
                        supplier,
                        TODAY,
                        "",
                        List.of(new StockReceiptRequest.ReceiptLineInput(
                                rice, "L-E", LocalDate.of(2027, 1, 1), new BigDecimal("1000"), BigDecimal.TEN))));
        receiptService.confirmReceipt(principal, receipt.id());

        CurrentUser vp = user("vp-e", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vp.userId(),
                campus);
        CurrentUser teacher = user("teacher-e", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacher.userId(),
                classroom);
        unlockService.unlockDay(
                vp, new DayUnlockRequest(classroom, TODAY, AFTER_CUTOFF.plus(Duration.ofHours(1)), "Quên lưu"));
        attendanceService.saveClassAttendance(teacher, classroom, request(present(child1), absent(child2)));

        mealCountService.confirm(vp, lunch(vp).id());

        // 1 suất × 60 g; gọi xác nhận lại bữa khác cùng ngày không ghi thêm
        assertThat(movementSum("DEDUCT")).isEqualByComparingTo("-60");

        attendanceService.saveClassAttendance(teacher, classroom, request(present(child1), present(child2)));
        mealCountService.confirm(vp, lunch(vp).id());

        assertThat(movementSum("DEDUCT")).isEqualByComparingTo("-60");
        assertThat(movementSum("ADJUST")).isEqualByComparingTo("-60");
        assertThat(jdbc.queryForObject(
                        "SELECT required_qty FROM stock_daily_demands WHERE campus_id = ? AND meal_date = ?",
                        BigDecimal.class,
                        campus,
                        TODAY))
                .isEqualByComparingTo("120");
    }

    private BigDecimal movementSum(String type) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM stock_movements WHERE movement_type = ?",
                BigDecimal.class,
                type);
    }

    private MealCountResponse lunch(CurrentUser user) {
        return mealCountService.getMealCounts(user, TODAY, null).counts().stream()
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

    private static Entry absent(long childId) {
        return new Entry(childId, AttendanceStatus.UNEXCUSED, Map.of());
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
        return new CurrentUser(
                id, "kc-" + username, username, username, null, Set.of(role), TestPermissions.defaultsOf(role));
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
