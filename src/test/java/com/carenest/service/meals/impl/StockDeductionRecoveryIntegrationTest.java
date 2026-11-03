package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.carenest.dto.attendance.DayUnlockRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest;
import com.carenest.dto.attendance.SaveAttendanceRequest.Entry;
import com.carenest.dto.meals.DayBoardResponse;
import com.carenest.dto.meals.MealCountResponse;
import com.carenest.dto.meals.StockReceiptRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.attendance.AttendanceDayUnlockService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.meals.DayBoardService;
import com.carenest.service.meals.MealCountService;
import com.carenest.service.meals.StockLedgerService;
import com.carenest.service.meals.StockReceiptService;
import com.carenest.utils.ApiCode;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Trừ kho sau commit lỗi (lock timeout, DB chập chờn) thì xác nhận suất vẫn thành công (P-38) nhưng kho không được trừ:
 * hub phải báo lệch thay vì "không có thực phẩm khô", và có thao tác đồng bộ lại trừ đúng một lần. Không bọc
 * @Transactional để listener sau commit chạy thật.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class StockDeductionRecoveryIntegrationTest {

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

    @MockitoSpyBean
    private StockLedgerService stockLedgerService;

    @Autowired
    private MealCountService mealCountService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceDayUnlockService unlockService;

    @Autowired
    private StockReceiptService receiptService;

    @Autowired
    private DayBoardService dayBoardService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("NUT-11, P-38: trừ kho sau xác nhận suất lỗi ⇒ hub báo chưa trừ (không phải 'không có thực phẩm khô')")
    void failedDeductionIsShownOnDayBoard() {
        long campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-F', 'F', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES ("
                + campus + ", " + year + ", 'Lớp F', " + ag3 + ") RETURNING id");
        long child =
                insertId("INSERT INTO children (full_name, date_of_birth) VALUES ('An', '2023-01-01') RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                child,
                classroom);
        long rice = insertId("INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size,"
                + " unit_price, kcal, protein, lipid, glucid, nutrient_source, storage, origin)"
                + " VALUES ('GAO-F', 'Gạo', 'GRAIN', 'g', 'kg', 1000, 20000, 350, 7, 1, 77, 'TEST', 'DRY', 'PLANT')"
                + " RETURNING id");
        long dish = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('COM-F', 'Cơm', 'CARB') RETURNING id");
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, 60)",
                dish,
                rice,
                ag3);
        long week = insertId("INSERT INTO meal_plan_weeks (week_start, status, published_at)"
                + " VALUES ('2026-10-19', 'PUBLISHED', now()) RETURNING id");
        long day = insertId(
                "INSERT INTO meal_plan_days (week_id, menu_date) VALUES (" + week + ", '2026-10-21') RETURNING id");
        jdbc.update(
                "INSERT INTO meal_plan_items (day_id, session, menu_block, dish_id) VALUES (?, 'LUNCH', 'KINDERGARTEN', ?)",
                day,
                dish);
        CurrentUser principal = user("principal-f", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        long supplier = insertId("INSERT INTO suppliers (name, phone) VALUES ('NCC F', '0900000003') RETURNING id");
        var receipt = receiptService.createReceipt(
                principal,
                new StockReceiptRequest(
                        campus,
                        supplier,
                        TODAY,
                        "",
                        List.of(new StockReceiptRequest.ReceiptLineInput(
                                rice, "L-F", LocalDate.of(2027, 1, 1), new BigDecimal("1000"), BigDecimal.TEN))));
        receiptService.confirmReceipt(principal, receipt.id());
        CurrentUser vp = user("vp-f", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vp.userId(),
                campus);
        CurrentUser teacher = user("teacher-f", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from, is_homeroom) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01', TRUE)",
                teacher.userId(),
                classroom);
        unlockService.unlockDay(
                vp, new DayUnlockRequest(classroom, TODAY, AFTER_CUTOFF.plus(Duration.ofHours(1)), "Quên lưu"));
        attendanceService.saveClassAttendance(teacher, classroom, request(present(child)));

        // Lần trừ kho đầu tiên sau commit gặp lỗi hạ tầng
        doThrow(new IllegalStateException("simulated lock timeout"))
                .doCallRealMethod()
                .when(stockLedgerService)
                .syncDay(any(), any());
        mealCountService.confirm(vp, lunch(vp).id());

        assertThat(movementSum("DEDUCT")).isEqualByComparingTo("0");
        DayBoardResponse.DryStock dryStock = dryStock(principal, campus);
        assertThat(dryStock.status()).isNotEqualTo("NOTHING_TO_DEDUCT").isEqualTo("NOT_SYNCED");
        assertThat(dryStock.foodCount()).isEqualTo(1);

        // Đồng bộ lại (PHT xác nhận suất có quyền): trừ đúng một lần, gọi thêm không trừ lặp
        stockLedgerService.resyncDay(vp, campus, TODAY);
        stockLedgerService.resyncDay(vp, campus, TODAY);
        assertThat(movementSum("DEDUCT")).isEqualByComparingTo("-60");
        assertThat(dryStock(principal, campus))
                .extracting(DayBoardResponse.DryStock::status, DayBoardResponse.DryStock::foodCount)
                .containsExactly("DEDUCTED", 1);
    }

    @Test
    @DisplayName("AUTH-20: giáo viên không đồng bộ lại kho được")
    void resyncRequiresStockOrMealCountPermission() {
        CurrentUser teacher = user("teacher-x", CareNestRole.TEACHER);
        long campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-X', 'X') RETURNING id");
        assertThatThrownBy(() -> stockLedgerService.resyncDay(teacher, campus, TODAY))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.STOCK_ACCESS_DENIED);
    }

    private DayBoardResponse.DryStock dryStock(CurrentUser user, long campus) {
        return dayBoardService
                .getDayBoard(user, TODAY, campus)
                .campuses()
                .getFirst()
                .dryStock();
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
