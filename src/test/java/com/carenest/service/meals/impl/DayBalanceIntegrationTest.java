package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.meals.DayBalanceRequest;
import com.carenest.dto.meals.DayBalanceResponse;
import com.carenest.dto.meals.DayBalanceStatusResponse;
import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.DayBalanceService;
import com.carenest.service.meals.MealPlanService;
import com.carenest.support.MovableClock;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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

/** Bản cân đối khẩu phần ngày: dựng từ công thức, tính thử, làm tròn, lưu, nhận biết thực đơn đã đổi. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class DayBalanceIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private DayBalanceService dayBalanceService;

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser kitchen;
    private long campus1;
    private long campus2;
    private long ag3;
    private long rice;
    private long pork;
    private long milk;
    private long com;
    private long thit;
    private long sua;

    private static final MovableClock CLOCK = new MovableClock();

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    @BeforeEach
    void setUp() {
        CLOCK.set(MONDAY, LocalTime.of(7, 0));
        campus1 = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'Cơ sở A', TRUE) RETURNING id");
        campus2 = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-B', 'Cơ sở B', FALSE) RETURNING id");
        ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        jdbc.update(
                "INSERT INTO school_year_campus_members (school_year_id, campus_id, code, name, short_name, address,"
                        + " phone, status) SELECT ?, id, code, name, short_name, address, phone, status FROM campuses",
                year);
        for (long campus : List.of(campus1, campus2)) {
            insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus + ", "
                    + year + ", 'Lớp " + campus + "', " + ag3 + ") RETURNING id");
        }
        // Ngân sách 6000đ đúng bằng chi phí công thức: cơm 1200 + thịt 800 + sữa 4000
        jdbc.update(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,"
                        + " protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max, glucid_pct_min, glucid_pct_max)"
                        + " VALUES (?, '2026-09-01', 6000, 300, 600, 12, 25, 15, 35, 40, 70)",
                ag3);
        rice = food("GAO", "Gạo", "DRY", "PLANT", "350", "7", "1", "77");
        pork = food("THIT", "Thịt", "FRESH", "ANIMAL", "250", "20", "18", "0");
        milk = food("SUA", "Sữa", "FRESH", "ANIMAL", "60", "3", "3", "5");
        com = dish("COM", "Cơm", "CARB", rice, 60);
        thit = dish("THITRIM", "Thịt rim", "MAIN", pork, 40);
        sua = dish("SUACHUA", "Sữa", "SNACK", milk, 200);
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        kitchen = user("kitchen", CareNestRole.KITCHEN_STAFF);
        mealPlanService.saveMealPlan(principal, MONDAY, menu(thit));
    }

    @Test
    @DisplayName("Chưa lưu: dựng từ công thức, đủ hai điểm trường, chấm đạt khi lượng, chất, tiền trong khoảng")
    void seedsFromRecipe() {
        DayBalanceResponse balance = dayBalanceService.getBalance(principal, MONDAY, 1);

        assertThat(balance.saved()).isFalse();
        assertThat(balance.version()).isNull();
        assertThat(balance.canEdit()).isTrue();
        assertThat(balance.campuses())
                .extracting(DayBalanceResponse.Campus::campusId)
                .containsExactly(campus1, campus2);
        assertThat(balance.counts())
                .hasSize(2)
                .allSatisfy(c -> assertThat(c.children()).isZero());
        assertThat(balance.lines()).hasSize(3);
        assertThat(balance.passed()).isTrue();
        assertThat(balance.groups()).singleElement().satisfies(g -> {
            assertThat(g.evaluation().moneyStatus()).isEqualTo("OK");
            assertThat(g.evaluation().cost()).isEqualByComparingTo("6000");
        });
    }

    @Test
    @DisplayName("Tính thử rồi lưu: làm tròn thực phẩm tươi trên tổng toàn trường, chia hai điểm trường khớp tổng")
    void previewRoundsAndSavePersists() {
        DayBalanceResponse preview = dayBalanceService.previewBalance(principal, MONDAY, 1, request(null, true));

        // Thịt 40 g × (31 + 22) = 2120 g ⇒ 2,2 kg; gạo là hàng khô giữ 3180 g
        DayBalanceResponse.SummaryRow porkRow = summary(preview, pork);
        assertThat(porkRow.purchaseTotal()).isEqualByComparingTo("2200");
        assertThat(porkRow.campuses().stream()
                        .map(DayBalanceResponse.CampusShare::purchaseTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("2200");
        assertThat(summary(preview, rice).purchaseTotal()).isEqualByComparingTo("3180");
        assertThat(dayBalanceService.getBalance(principal, MONDAY, 1).saved()).isFalse();

        DayBalanceResponse saved = dayBalanceService.saveBalance(principal, MONDAY, 1, request(null, true));

        assertThat(saved.saved()).isTrue();
        assertThat(saved.roundingStep()).isEqualByComparingTo("0.1");
        assertThat(summary(saved, pork).purchaseTotal()).isEqualByComparingTo("2200");
        assertThat(saved.counts())
                .extracting(DayBalanceResponse.Count::children)
                .containsExactly(31, 22);
        DayBalanceResponse reread = dayBalanceService.getBalance(principal, MONDAY, 1);
        assertThat(reread.version()).isEqualTo(saved.version());
        assertThat(reread.lines()).containsExactlyElementsOf(saved.lines());
        assertThat(dayBalanceService.getStatuses(principal, MONDAY, MONDAY))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.saved()).isTrue();
                    assertThat(s.passed()).isTrue();
                });
    }

    @Test
    @DisplayName("Sửa gam làm ngày không đạt tiền: trạng thái nêu nhóm tuổi và tiêu chí")
    void editingGramsFailsMoney() {
        DayBalanceRequest edit = new DayBalanceRequest(
                null,
                null,
                null,
                List.of(new DayBalanceRequest.EditInput(ag3, milk, new BigDecimal("300"), null)),
                false,
                null);

        DayBalanceResponse saved = dayBalanceService.saveBalance(principal, MONDAY, 1, edit);

        assertThat(saved.passed()).isFalse();
        DayBalanceStatusResponse status =
                dayBalanceService.getStatuses(principal, MONDAY, MONDAY).getFirst();
        assertThat(status.problems()).anySatisfy(p -> assertThat(p).contains("tiền"));
    }

    @Test
    @DisplayName("Đổi món sau khi lưu: bản cân đối cũ, món còn lại giữ số đã sửa, món mới lấy theo công thức")
    void menuChangeMarksStale() {
        dayBalanceService.saveBalance(
                principal,
                MONDAY,
                1,
                new DayBalanceRequest(
                        null,
                        null,
                        null,
                        List.of(new DayBalanceRequest.EditInput(ag3, rice, new BigDecimal("70"), null)),
                        false,
                        null));
        long egg = food("TRUNG", "Trứng", "FRESH", "ANIMAL", "150", "13", "11", "1");
        long trung = dish("TRUNGRAN", "Trứng rán", "MAIN", egg, 30);

        mealPlanService.saveMealPlan(principal, MONDAY, menu(trung));

        DayBalanceResponse balance = dayBalanceService.getBalance(principal, MONDAY, 1);
        assertThat(balance.stale()).isTrue();
        assertThat(balance.lines())
                .extracting(DayBalanceResponse.Line::foodId)
                .containsExactlyInAnyOrder(rice, egg, milk);
        assertThat(balance.lines().stream()
                        .filter(l -> l.foodId() == rice)
                        .findFirst()
                        .orElseThrow()
                        .grams())
                .isEqualByComparingTo("70");
        assertThat(dayBalanceService
                        .getStatuses(principal, MONDAY, MONDAY)
                        .getFirst()
                        .passed())
                .isFalse();
    }

    @Test
    @DisplayName("Lưu bằng phiên bản cũ, ngày đã qua, ngày chưa có món hoặc thiếu quyền đều bị từ chối")
    void rejectsInvalidSaves() {
        dayBalanceService.saveBalance(principal, MONDAY, 1, request(null, false));

        expectCode(
                () -> dayBalanceService.saveBalance(principal, MONDAY, 1, request(null, false)),
                ApiCode.DAY_BALANCE_CONFLICT);
        expectCode(
                () -> dayBalanceService.saveBalance(principal, MONDAY.plusDays(1), 1, request(null, false)),
                ApiCode.DAY_BALANCE_NO_MENU);
        expectCode(() -> dayBalanceService.saveBalance(kitchen, MONDAY, 1, request(null, false)), ApiCode.FORBIDDEN);
        CLOCK.set(MONDAY.plusDays(1), LocalTime.of(7, 0));
        expectCode(
                () -> dayBalanceService.saveBalance(principal, MONDAY, 1, request(0L, false)),
                ApiCode.DAY_BALANCE_LOCKED);
    }

    private DayBalanceRequest request(Long version, boolean round) {
        return new DayBalanceRequest(
                version,
                List.of(
                        new DayBalanceRequest.CountInput(campus1, ag3, 31),
                        new DayBalanceRequest.CountInput(campus2, ag3, 22)),
                null,
                null,
                round,
                new BigDecimal("0.1"));
    }

    private static DayBalanceResponse.SummaryRow summary(DayBalanceResponse balance, long foodId) {
        return balance.summary().stream()
                .filter(r -> r.foodId() == foodId)
                .findFirst()
                .orElseThrow();
    }

    private MealPlanRequest menu(long mainDish) {
        List<MealPlanRequest.ItemInput> items = new ArrayList<>();
        items.add(new MealPlanRequest.ItemInput(MealSession.LUNCH, MenuBlock.KINDERGARTEN, com));
        items.add(new MealPlanRequest.ItemInput(MealSession.LUNCH, MenuBlock.KINDERGARTEN, mainDish));
        items.add(new MealPlanRequest.ItemInput(MealSession.AFTERNOON, MenuBlock.KINDERGARTEN, sua));
        return new MealPlanRequest(List.of(new MealPlanRequest.DayInput(MONDAY, false, "", items)));
    }

    private long food(
            String code, String name, String storage, String origin, String kcal, String p, String l, String g) {
        long foodId = insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, storage, origin) VALUES ('" + code + "', '"
                        + name
                        + "', 'OTHER', 'g', 'kg', 1000, 20000, " + kcal + ", " + p + ", " + l + ", " + g + ", 'TEST', '"
                        + storage + "', '" + origin + "') RETURNING id");
        jdbc.update(
                "INSERT INTO food_prices (food_id, price, effective_from, reason)"
                        + " SELECT id, unit_price, DATE '2000-01-01', 'Giá ban đầu' FROM foods WHERE id = ?",
                foodId);
        return foodId;
    }

    private long dish(String code, String name, String type, long foodId, int grams) {
        long id = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('" + code + "', '" + name + "', '" + type
                + "') RETURNING id");
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

    private static void expectCode(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(apiCode);
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
