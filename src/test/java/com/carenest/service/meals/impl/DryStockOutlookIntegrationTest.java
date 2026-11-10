package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.meals.DayBoardResponse;
import com.carenest.dto.meals.FreshForecastRequest;
import com.carenest.dto.meals.FreshForecastResponse;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.StockReceiptRequest;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.DayBoardService;
import com.carenest.service.meals.FreshGoodsService;
import com.carenest.service.meals.MealPlanService;
import com.carenest.service.meals.StockReceiptService;
import com.carenest.utils.enums.CareNestRole;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * Kho khô sắp thiếu (NUT-11, P-38): hàng khô cần nhập thêm trong bản dự trù (P-24 user đổi 08/11/2026), cảnh báo trong
 * kế hoạch tuần và trên trang Bán trú hôm nay. Hôm nay cố định Chủ nhật 08/11/2026, tuần tính là 09–13/11.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class DryStockOutlookIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate FRIDAY = MONDAY.plusDays(4);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-08T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private FreshGoodsService freshService;

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private DayBoardService dayBoardService;

    @Autowired
    private StockReceiptService receiptService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private long campus;
    private long year;
    private long classroom;
    private long ag3;
    private long rice;
    private long supplier;

    @BeforeEach
    void setUp() {
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-K', 'Cơ sở K', TRUE) RETURNING id");
        ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'Lớp K', " + ag3 + ") RETURNING id");
        for (String name : List.of("Trẻ A", "Trẻ B")) {
            long child = insertId(
                    "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), '"
                            + name + "', '2022-06-01', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
            jdbc.update(
                    "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                    child,
                    classroom);
        }
        // Gạo kho khô 60 g/trẻ, thải bỏ 20% ⇒ mua 75 g/trẻ; 2 trẻ ⇒ 150 g/ngày
        rice = insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin) VALUES ('GAO',"
                        + " 'Gạo', 'OTHER', 'g', 'kg', 1000, 20000, 100, 5, 1, 10, 'TEST', 20, 'DRY', 'PLANT')"
                        + " RETURNING id");
        long com = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('COM', 'Cơm', 'MAIN') RETURNING id");
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, 60)",
                com,
                rice,
                ag3);
        week(MONDAY, "PUBLISHED", com);
        supplier = insertId("INSERT INTO suppliers (name, phone) VALUES ('NCC khô', '0900000009') RETURNING id");
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        // Tồn 375 g ⇒ đủ thứ Hai (150), thứ Ba (150), thiếu từ thứ Tư
        receive("375", "2027-05-01");
    }

    @Test
    @DisplayName("NUT-11, P-24: bản dự trù có hàng khô cần nhập thêm — cộng dồn theo ngày, thiếu từ thứ Tư")
    void forecastListsDryShortFromFirstMissingDay() {
        FreshForecastResponse enrolled = freshService.previewForecast(principal, request(null));

        assertThat(enrolled.dryLines()).singleElement().satisfies(line -> {
            assertThat(line.food().code()).isEqualTo("GAO");
            assertThat(line.requiredQty()).isEqualByComparingTo("750");
            assertThat(line.availableQty()).isEqualByComparingTo("375");
            assertThat(line.shortQty()).isEqualByComparingTo("375");
            assertThat(line.shortFrom()).isEqualTo(MONDAY.plusDays(2));
        });

        // Số dự kiến của bản dự trù 4 trẻ ⇒ 300 g/ngày, thiếu từ thứ Ba
        FreshForecastResponse expected = freshService.previewForecast(
                principal, request(List.of(new FreshForecastRequest.FreshForecastCountInput(ag3, 4))));
        assertThat(expected.dryLines()).singleElement().satisfies(line -> {
            assertThat(line.shortQty()).isEqualByComparingTo("1125");
            assertThat(line.shortFrom()).isEqualTo(MONDAY.plusDays(1));
        });
    }

    @Test
    @DisplayName("NUT-12: hàng khô mua theo bao ⇒ lượng nhập thêm làm tròn lên nguyên bao, cần/tồn giữ nguyên")
    void dryShortRoundsUpToWholePurchaseUnit() {
        jdbc.update("UPDATE foods SET purchase_unit = 'bao 1 kg', purchase_unit_size = 1000 WHERE id = ?", rice);
        // Phiếu nhập ở setUp đã nạp thực phẩm vào phiên JPA; bỏ bản cũ để đọc đơn vị mua vừa sửa bằng SQL
        entityManager.clear();

        FreshForecastResponse forecast = freshService.previewForecast(principal, request(null));

        assertThat(forecast.dryLines()).singleElement().satisfies(line -> {
            assertThat(line.requiredQty()).isEqualByComparingTo("750");
            assertThat(line.availableQty()).isEqualByComparingTo("375");
            assertThat(line.shortQty()).isEqualByComparingTo("1000");
        });
    }

    @Test
    @DisplayName("NUT-11: kho đủ ⇒ không có dòng hàng khô; kỳ đã qua ⇒ không tính")
    void enoughStockOrPastPeriodHasNoDryLines() {
        receive("1000", "2027-05-01");
        assertThat(freshService.previewForecast(principal, request(null)).dryLines())
                .isEmpty();

        FreshForecastResponse past = freshService.previewForecast(
                principal, new FreshForecastRequest(campus, TODAY.minusDays(6), TODAY.minusDays(2), "", null));
        assertThat(past.dryLines()).isEmpty();
    }

    @Test
    @DisplayName("NUT-11, P-38: ngày đã chốt suất không tính lại nhu cầu dự kiến")
    void confirmedDayIsSkipped() {
        long count =
                insertId("INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, aggregated_at,"
                        + " confirmed_at) VALUES (" + campus + ", " + year + ", '" + MONDAY
                        + "', 'LUNCH', 'CONFIRMED', now(), now()) RETURNING id");
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal,"
                        + " substitute) VALUES (?, ?, FALSE, 2, 0, 0, 2, 0)",
                count,
                classroom);

        // Thứ Hai đã chốt (không trừ ở đây vì chèn thẳng) ⇒ còn thứ Ba–Sáu 600 g, tồn 375 ⇒ thiếu từ thứ Năm
        assertThat(freshService.previewForecast(principal, request(null)).dryLines())
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.requiredQty()).isEqualByComparingTo("600");
                    assertThat(line.shortFrom()).isEqualTo(MONDAY.plusDays(3));
                });
    }

    @Test
    @DisplayName("NUT-11, P-38, NUT-08: kế hoạch tuần cảnh báo kho khô sắp thiếu theo điểm trường, chỉ là cảnh báo")
    void mealPlanWarnsDryStockShort() {
        MealPlanResponse plan = mealPlanService.getMealPlan(principal, MONDAY);

        assertThat(plan.issues())
                .filteredOn(i -> "DRY_STOCK_SHORT".equals(i.code()))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.severity()).isEqualTo("WARNING");
                    assertThat(issue.date()).isEqualTo(MONDAY.plusDays(2));
                    assertThat(issue.message()).contains("Cơ sở K", "1 thực phẩm", "thứ Tư 11/11");
                });

        receive("1000", "2027-05-01");
        assertThat(mealPlanService.getMealPlan(principal, MONDAY).issues())
                .noneMatch(i -> "DRY_STOCK_SHORT".equals(i.code()));
    }

    @Test
    @DisplayName("NUT-11: Bán trú hôm nay báo số thực phẩm khô sắp thiếu và ngày bắt đầu thiếu")
    void dayBoardShowsUpcomingShort() {
        DayBoardResponse.DryStock stock = dryStock();
        assertThat(stock.upcomingShortFoodCount()).isEqualTo(1);
        assertThat(stock.upcomingShortFrom()).isEqualTo(MONDAY.plusDays(2));

        receive("1000", "2027-05-01");
        DayBoardResponse.DryStock enough = dryStock();
        assertThat(enough.upcomingShortFoodCount()).isZero();
        assertThat(enough.upcomingShortFrom()).isNull();
    }

    private DayBoardResponse.DryStock dryStock() {
        return dayBoardService.getDayBoard(principal, TODAY, campus).campuses().stream()
                .filter(c -> c.campusId().equals(campus))
                .findFirst()
                .orElseThrow()
                .dryStock();
    }

    private void receive(String grams, String expiry) {
        StockReceiptRequest request = new StockReceiptRequest(
                campus,
                supplier,
                LocalDate.of(2026, 11, 2),
                "",
                List.of(new StockReceiptRequest.ReceiptLineInput(
                        rice,
                        "L" + System.nanoTime(),
                        LocalDate.parse(expiry),
                        new BigDecimal(grams),
                        new BigDecimal("20000"))));
        receiptService.confirmReceipt(
                principal, receiptService.createReceipt(principal, request).id());
    }

    private FreshForecastRequest request(List<FreshForecastRequest.FreshForecastCountInput> counts) {
        return new FreshForecastRequest(campus, MONDAY, FRIDAY, "", counts);
    }

    /** Thứ Hai–Sáu: bữa trưa khối mẫu giáo. */
    private void week(LocalDate start, String status, long dish) {
        long week = insertId("INSERT INTO meal_plan_weeks (week_start, status, published_at) VALUES ('" + start + "', '"
                + status + "', " + ("PUBLISHED".equals(status) ? "now()" : "NULL") + ") RETURNING id");
        for (int i = 0; i < 5; i++) {
            long day = insertId("INSERT INTO meal_plan_days (week_id, menu_date) VALUES (" + week + ", '"
                    + start.plusDays(i) + "') RETURNING id");
            jdbc.update(
                    "INSERT INTO meal_plan_items (day_id, session, menu_block, dish_id) VALUES (?, 'LUNCH', 'KINDERGARTEN', ?)",
                    day,
                    dish);
        }
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
