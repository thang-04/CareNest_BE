package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.meals.DayMarketResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.DayMarketService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Bảng đi chợ theo ngày: lượng và tiền theo nhóm tuổi, nguồn số suất, thu–chi, trạng thái ngày. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class DayMarketIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate DRAFT_MONDAY = MONDAY.plusWeeks(1);
    private static final LocalDate NO_MENU_MONDAY = MONDAY.plusWeeks(2);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private DayMarketService dayMarketService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private CurrentUser principal;
    private CurrentUser vp;
    private long campus;
    private long otherCampus;
    private long year;
    private long classroom;
    private long ag3;
    private long pork;

    @BeforeEach
    void setUp() {
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-M', 'Cơ sở M', TRUE) RETURNING id");
        otherCampus =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-N', 'Cơ sở N', FALSE) RETURNING id");
        ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'Lớp M', " + ag3 + ") RETURNING id");
        for (String name : List.of("Trẻ A", "Trẻ B")) {
            long child = insertId(
                    "INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) VALUES ('IT' || substr(md5(random()::text), 1, 12), '"
                            + name + "', '2022-06-01', 'MALE', 'ACTIVE', CURRENT_DATE) RETURNING id");
            jdbc.update(
                    "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                    child,
                    classroom);
        }
        // Mọi thực phẩm 20.000đ/kg. Gạo kho khô thải bỏ 20%; thịt tươi không thải bỏ; rau tươi thải bỏ 20%
        long rice = food("GAO", "Gạo", "DRY", "20");
        pork = food("THIT", "Thịt", "FRESH", "0");
        long veg = food("RAU", "Rau", "FRESH", "20");
        long com = dish("COM", "Cơm", rice, 60);
        long thit = dish("THITRIM", "Thịt rim", pork, 40);
        long rau = dish("RAUXAO", "Rau xào", veg, 50);
        week(MONDAY, "PUBLISHED", com, thit, rau);
        week(DRAFT_MONDAY, "DRAFT", com, thit, rau);
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vp = user("vp", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                vp.userId(),
                campus);
    }

    @Test
    @DisplayName("Chưa chốt suất ⇒ tính theo sĩ số đang học; hàng tươi trước, hàng khô sau; tiền theo lượng thực dùng")
    void forecastBasisUsesEnrolmentAndSplitsFreshAndDry() {
        mealPrice(30000);

        DayMarketResponse market = dayMarketService.getDayMarket(principal, campus, MONDAY);

        assertThat(market.status()).isEqualTo("READY");
        assertThat(market.basis()).isEqualTo("FORECAST");
        assertThat(market.confirmedAt()).isNull();
        assertThat(market.groups()).singleElement().satisfies(g -> {
            assertThat(g.ageGroupId()).isEqualTo(ag3);
            assertThat(g.servings()).isEqualTo(2);
            assertThat(g.mealPrice()).isEqualByComparingTo("30000");
            assertThat(g.revenue()).isEqualByComparingTo("60000");
        });
        // Rau 2 × 50 g ÷ 0,8 = 125 g ⇒ 2.500đ; thịt 2 × 40 g = 80 g ⇒ 1.600đ; gạo 2 × 60 g ÷ 0,8 = 150 g ⇒ 3.000đ
        assertThat(market.lines())
                .extracting(
                        l -> l.food().code(),
                        DayMarketResponse.DayMarketLine::storage,
                        l -> l.quantity().intValue(),
                        l -> l.cost().intValue())
                .containsExactly(
                        tuple("RAU", "FRESH", 125, 2500),
                        tuple("THIT", "FRESH", 80, 1600),
                        tuple("GAO", "DRY", 150, 3000));
        assertThat(market.lines()).allSatisfy(l -> {
            assertThat(l.unitPrice()).isEqualByComparingTo("20000");
            assertThat(l.groups()).singleElement().satisfies(share -> {
                assertThat(share.ageGroupId()).isEqualTo(ag3);
                assertThat(share.quantity()).isEqualByComparingTo(l.quantity());
                assertThat(share.cost()).isEqualByComparingTo(l.cost());
            });
        });
        DayMarketResponse.DayMarketTotals totals = market.totals();
        assertThat(totals.servings()).isEqualTo(2);
        assertThat(totals.freshCost()).isEqualByComparingTo("4100");
        assertThat(totals.dryCost()).isEqualByComparingTo("3000");
        assertThat(totals.cost()).isEqualByComparingTo("7100");
        assertThat(totals.revenue()).isEqualByComparingTo("60000");
        assertThat(totals.balance()).isEqualByComparingTo("52900");
        assertThat(totals.groups())
                .singleElement()
                .satisfies(g -> assertThat(g.cost()).isEqualByComparingTo("7100"));
        assertThat(market.menu()).singleElement().satisfies(m -> {
            assertThat(m.session()).isEqualTo(MealSession.LUNCH);
            assertThat(m.groups())
                    .singleElement()
                    .satisfies(g -> assertThat(g.dishes()).containsExactly("Cơm", "Thịt rim", "Rau xào"));
        });
    }

    @Test
    @DisplayName("NUT-03: sổ chưa xác nhận không dùng; đã xác nhận ⇒ tính theo suất chốt kèm lúc xác nhận")
    void confirmedBasisUsesConfirmedServings() {
        long count = insertCount("PENDING_CONFIRMATION");
        addLine(count, 10);
        assertThat(dayMarketService.getDayMarket(principal, campus, MONDAY).basis())
                .isEqualTo("FORECAST");

        jdbc.update("UPDATE meal_counts SET status = 'CONFIRMED', confirmed_at = now() WHERE id = ?", count);
        // Sổ vừa đọc còn trong persistence context; bỏ đi để lần đọc sau thấy trạng thái mới
        entityManager.clear();
        DayMarketResponse market = dayMarketService.getDayMarket(principal, campus, MONDAY);

        assertThat(market.basis()).isEqualTo("CONFIRMED");
        assertThat(market.confirmedAt()).isNotNull();
        assertThat(market.groups())
                .singleElement()
                .satisfies(g -> assertThat(g.servings()).isEqualTo(10));
        assertThat(market.lines())
                .extracting(l -> l.food().code(), l -> l.quantity().intValue())
                .containsExactly(tuple("RAU", 625), tuple("THIT", 400), tuple("GAO", 750));
    }

    @Test
    @DisplayName(
            "NUT-12: đơn vị mua đếm được ⇒ lượng cần mua làm tròn lên nguyên đơn vị, tiền vẫn theo lượng thực dùng")
    void countablePurchaseUnitRoundsOrderQuantityOnly() {
        jdbc.update("UPDATE foods SET purchase_unit = 'hộp 150 g', purchase_unit_size = 150 WHERE id = ?", pork);
        long count = insertCount("CONFIRMED");
        addLine(count, 10);

        DayMarketResponse market = dayMarketService.getDayMarket(principal, campus, MONDAY);

        // Thịt 400 g ⇒ 3 hộp 150 g = 450 g; tiền 400 ÷ 150 × 20.000 = 53.333đ
        assertThat(market.lines())
                .filteredOn(l -> "THIT".equals(l.food().code()))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.quantity()).isEqualByComparingTo("400");
                    assertThat(l.orderQuantity()).isEqualByComparingTo("450");
                    assertThat(l.cost()).isEqualByComparingTo("53333");
                });
    }

    @Test
    @DisplayName("NUT-17: nhóm chưa có giá tiền ăn ⇒ không có tiền thu và không tính thừa thiếu")
    void missingMealPriceLeavesRevenueAndBalanceEmpty() {
        DayMarketResponse market = dayMarketService.getDayMarket(principal, campus, MONDAY);

        assertThat(market.groups()).singleElement().satisfies(g -> {
            assertThat(g.mealPrice()).isNull();
            assertThat(g.revenue()).isNull();
        });
        assertThat(market.totals().revenue()).isNull();
        assertThat(market.totals().balance()).isNull();
        assertThat(market.totals().cost()).isEqualByComparingTo("7100");
    }

    @Test
    @DisplayName("Thứ Bảy ⇒ DAY_OFF; tuần nháp hoặc chưa có thực đơn ⇒ NO_MENU; không có dòng thực phẩm")
    void dayOffAndMissingMenuHaveNoLines() {
        DayMarketResponse saturday = dayMarketService.getDayMarket(principal, campus, MONDAY.plusDays(5));
        assertThat(saturday.status()).isEqualTo("DAY_OFF");
        assertThat(saturday.basis()).isNull();
        assertThat(saturday.lines()).isEmpty();
        assertThat(saturday.groups()).isEmpty();

        assertThat(dayMarketService
                        .getDayMarket(principal, campus, DRAFT_MONDAY)
                        .status())
                .isEqualTo("NO_MENU");
        DayMarketResponse none = dayMarketService.getDayMarket(principal, campus, NO_MENU_MONDAY);
        assertThat(none.status()).isEqualTo("NO_MENU");
        assertThat(none.lines()).isEmpty();
        assertThat(none.totals().cost()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName(
            "AUTH-20: PHT còn phân công đọc mọi điểm trường; hết phân công bị chặn; điểm trường không tồn tại ⇒ 404")
    void readFollowsCampusAccess() {
        assertThat(dayMarketService.getDayMarket(vp, campus, MONDAY).status()).isEqualTo("READY");
        // Điểm trường khác chưa có lớp ⇒ đọc được nhưng không có suất
        assertThat(dayMarketService.getDayMarket(vp, otherCampus, MONDAY).lines())
                .isEmpty();
        CurrentUser unassigned = user("vp-unassigned", CareNestRole.VICE_PRINCIPAL);
        assertThatThrownBy(() -> dayMarketService.getDayMarket(unassigned, campus, MONDAY))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CAMPUS_ACCESS_DENIED);
        assertThatThrownBy(() -> dayMarketService.getDayMarket(principal, -1L, MONDAY))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.CAMPUS_NOT_FOUND);
    }

    private void mealPrice(int price) {
        jdbc.update(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,"
                        + " protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max, glucid_pct_min, glucid_pct_max)"
                        + " VALUES (?, '2026-09-01', ?, 300, 600, 12, 25, 15, 35, 40, 70)",
                ag3,
                price);
    }

    private long insertCount(String status) {
        return insertId("INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, aggregated_at,"
                + " confirmed_at) VALUES (" + campus + ", " + year + ", '" + MONDAY + "', 'LUNCH', '" + status
                + "', now(), " + ("CONFIRMED".equals(status) ? "now()" : "NULL") + ") RETURNING id");
    }

    private void addLine(long countId, int normal) {
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal,"
                        + " substitute) VALUES (?, ?, FALSE, ?, 0, 0, ?, 0)",
                countId,
                classroom,
                normal,
                normal);
    }

    /** Thứ Hai–Sáu: bữa trưa khối mẫu giáo gồm các món truyền vào. */
    private void week(LocalDate start, String status, long... dishes) {
        long week = insertId("INSERT INTO meal_plan_weeks (week_start, status, published_at) VALUES ('" + start + "', '"
                + status + "', " + ("PUBLISHED".equals(status) ? "now()" : "NULL") + ") RETURNING id");
        for (int i = 0; i < 5; i++) {
            LocalDate day = start.plusDays(i);
            jdbc.update(
                    "UPDATE meal_plan_weeks SET days = days || jsonb_build_array("
                            + "jsonb_build_object('date', ?::date, 'off', false, 'note', '')) WHERE id = ?",
                    day,
                    week);
            for (long dish : dishes) {
                jdbc.update(
                        "INSERT INTO meal_plan_items (week_id, menu_date, session, menu_block, dish_id)"
                                + " VALUES (?, ?, 'LUNCH', 'KINDERGARTEN', ?)",
                        week,
                        day,
                        dish);
            }
        }
    }

    private long food(String code, String name, String storage, String wastePct) {
        return insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, waste_pct, storage, origin) VALUES ('" + code
                        + "', '" + name + "', 'OTHER', 'g', 'kg', 1000, 20000, 100, 5, 1, 10, 'TEST', " + wastePct
                        + ", '" + storage + "', 'PLANT') RETURNING id");
    }

    private long dish(String code, String name, long foodId, int grams) {
        long id = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('" + code + "', '" + name
                + "', 'MAIN') RETURNING id");
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, ?)",
                id,
                foodId,
                ag3,
                grams);
        return id;
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
