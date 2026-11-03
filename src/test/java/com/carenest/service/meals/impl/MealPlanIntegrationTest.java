package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.meals.DayBoardResponse;
import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.DayBoardService;
import com.carenest.service.meals.MealPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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

/** Kế hoạch tuần theo khối, kiểm và công bố cả tuần (NUT-08), trang Bán trú hôm nay. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class MealPlanIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private DayBoardService dayBoardService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vp;
    private CurrentUser kitchen;
    private long campus;
    private long year;
    private long classroom;
    private long ag3;
    private long com;
    private long thit;
    private long sua;
    private long banh;

    @BeforeEach
    void setUp() {
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-M', 'Cơ sở M', TRUE) RETURNING id");
        ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'Lớp M', " + ag3 + ") RETURNING id");
        // Định mức ag-3 (mẫu giáo bé, bữa trưa + chiều): 300–600 kcal, P 12–25%, L 15–35%, G 40–70%
        jdbc.update(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,"
                        + " protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max, glucid_pct_min, glucid_pct_max)"
                        + " VALUES (?, '2026-09-01', 30000, 300, 600, 12, 25, 15, 35, 40, 70)",
                ag3);
        long rice = food("GAO", "Gạo", "DRY", "PLANT", "350", "7", "1", "77");
        long pork = food("THIT", "Thịt", "FRESH", "ANIMAL", "250", "20", "18", "0");
        long milk = food("SUA", "Sữa", "FRESH", "ANIMAL", "60", "3", "3", "5");
        com = dish("COM", "Cơm", "CARB", rice, 60);
        thit = dish("THITRIM", "Thịt rim", "MAIN", pork, 40);
        sua = dish("SUACHUA", "Sữa", "SNACK", milk, 200);
        banh = dish("BANH", "Bánh gạo", "SNACK", rice, 200);
        principal = user("principal", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principal.userId());
        vp = user("vp", CareNestRole.VICE_PRINCIPAL);
        campusRole(vp, "VICE_PRINCIPAL");
        kitchen = user("kitchen", CareNestRole.KITCHEN_STAFF);
        campusRole(kitchen, "KITCHEN_STAFF");
    }

    @Test
    @DisplayName("NUT-08: ngày chưa cân đối ⇒ chặn công bố, nêu ngày + nhóm tuổi; đạt hết ⇒ công bố cả tuần, khóa sửa")
    void publishBlockedThenPublishedThenLocked() {
        // Bữa chiều bánh gạo 200 g ⇒ 1010 kcal > 600
        MealPlanResponse draft = mealPlanService.saveMealPlan(principal, MONDAY, mondayOnly(banh));
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.publishable()).isFalse();
        assertThat(draft.issues())
                .filteredOn(i -> "NOT_BALANCED".equals(i.code()))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.date()).isEqualTo(MONDAY);
                    assertThat(i.ageGroupId()).isEqualTo(ag3);
                    assertThat(i.message()).contains("Năng lượng cao hơn định mức");
                });
        expectCode(() -> mealPlanService.publishMealPlan(principal, MONDAY), ApiCode.MEAL_PLAN_NOT_PUBLISHABLE);

        // Đổi bữa chiều sang sữa ⇒ 430 kcal, P 16,9%, L 28,9%, G 52,3%
        MealPlanResponse fixed = mealPlanService.saveMealPlan(principal, MONDAY, mondayOnly(sua));
        assertThat(fixed.evaluations()).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo("OK");
            assertThat(e.totals().kcal()).isEqualByComparingTo("430");
        });
        MealPlanResponse published = mealPlanService.publishMealPlan(principal, MONDAY);

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.canEdit()).isFalse();
        assertThat(published.canPublish()).isFalse();
        expectCode(() -> mealPlanService.saveMealPlan(principal, MONDAY, mondayOnly(sua)), ApiCode.MEAL_PLAN_PUBLISHED);
    }

    @Test
    @DisplayName("AUTH-20: PHT chưa được cấp MENU_MANAGE xem được, không lập/công bố được")
    void editRequiresMenuManage() {
        MealPlanResponse view = mealPlanService.getMealPlan(vp, MONDAY);
        assertThat(view.status()).isEqualTo("NONE");
        assertThat(view.days()).hasSize(5);
        assertThat(view.canEdit()).isFalse();
        // Chỉ khối có lớp năm học hiện hành (ag-3 mẫu giáo) mới có lưới và bảng kiểm
        assertThat(view.blocks()).singleElement().satisfies(b -> {
            assertThat(b.block()).isEqualTo(MenuBlock.KINDERGARTEN);
            assertThat(b.sessions()).containsExactly(MealSession.LUNCH, MealSession.AFTERNOON);
        });

        expectCode(() -> mealPlanService.saveMealPlan(vp, MONDAY, mondayOnly(sua)), ApiCode.FORBIDDEN);
        expectCode(() -> mealPlanService.publishMealPlan(vp, MONDAY), ApiCode.FORBIDDEN);
    }

    @Test
    @DisplayName("NUT-13: bữa không thuộc khối, ngày ngoài tuần, tuần không bắt đầu thứ Hai bị từ chối")
    void rejectsInvalidGrid() {
        MealPlanRequest morningForKindergarten = new MealPlanRequest(List.of(new MealPlanRequest.DayInput(
                MONDAY,
                false,
                "",
                List.of(new MealPlanRequest.ItemInput(MealSession.MORNING, MenuBlock.KINDERGARTEN, com)))));
        expectCode(
                () -> mealPlanService.saveMealPlan(principal, MONDAY, morningForKindergarten),
                ApiCode.MEAL_PLAN_SESSION_INVALID);
        MealPlanRequest saturday =
                new MealPlanRequest(List.of(new MealPlanRequest.DayInput(MONDAY.plusDays(5), false, "", List.of())));
        expectCode(() -> mealPlanService.saveMealPlan(principal, MONDAY, saturday), ApiCode.MEAL_PLAN_DATE_INVALID);
        expectCode(() -> mealPlanService.getMealPlan(principal, MONDAY.plusDays(1)), ApiCode.MEAL_PLAN_WEEK_INVALID);
    }

    @Test
    @DisplayName("P-38: suất đã chốt trước khi công bố ⇒ công bố trừ kho; hub hiện thực đơn, suất, kho thiếu")
    void dayBoardShowsMenuCountsAndStock() {
        insertConfirmedLunch(10);
        mealPlanService.saveMealPlan(principal, MONDAY, mondayOnly(sua));

        DayBoardResponse.CampusBoard before = board(principal);
        assertThat(before.menu().status()).isEqualTo("DRAFT");
        assertThat(before.dryStock().status()).isEqualTo("NO_MENU");

        mealPlanService.publishMealPlan(principal, MONDAY);
        DayBoardResponse.CampusBoard after = board(principal);

        assertThat(after.campusName()).isEqualTo("Cơ sở M");
        assertThat(after.menu().status()).isEqualTo("PUBLISHED");
        assertThat(after.menu().nextWeekStatus()).isEqualTo("NONE");
        assertThat(after.mealCounts().status()).isEqualTo("CONFIRMED");
        assertThat(after.mealCounts().totalServings()).isEqualTo(10);
        // Kho chưa có gạo ⇒ 600 g thiếu, xác nhận suất không bị chặn
        assertThat(after.dryStock().status()).isEqualTo("SHORTFALL");
        assertThat(after.dryStock().shortfallFoodCount()).isEqualTo(1);
        assertThat(after.freshReceiptStatus()).isEqualTo("NOT_AVAILABLE");
        assertThat(after.foodSampleStatus()).isEqualTo("NOT_AVAILABLE");
        assertThat(board(vp).dryStock().status()).isEqualTo("SHORTFALL");
        expectCode(() -> dayBoardService.getDayBoard(kitchen, MONDAY, campus), ApiCode.CAMPUS_ACCESS_DENIED);
    }

    private DayBoardResponse.CampusBoard board(CurrentUser user) {
        return dayBoardService.getDayBoard(user, MONDAY, campus).campuses().stream()
                .filter(c -> c.campusId().equals(campus))
                .findFirst()
                .orElseThrow();
    }

    /** Thứ Hai: trưa cơm + thịt, chiều {@code afternoonDish}; thứ Ba–Sáu nghỉ. */
    private MealPlanRequest mondayOnly(long afternoonDish) {
        List<MealPlanRequest.DayInput> days = new ArrayList<>();
        days.add(new MealPlanRequest.DayInput(
                MONDAY,
                false,
                "",
                List.of(
                        new MealPlanRequest.ItemInput(MealSession.LUNCH, MenuBlock.KINDERGARTEN, com),
                        new MealPlanRequest.ItemInput(MealSession.LUNCH, MenuBlock.KINDERGARTEN, thit),
                        new MealPlanRequest.ItemInput(MealSession.AFTERNOON, MenuBlock.KINDERGARTEN, afternoonDish))));
        for (int i = 1; i < 5; i++) {
            days.add(new MealPlanRequest.DayInput(MONDAY.plusDays(i), true, "Nghỉ", List.of()));
        }
        return new MealPlanRequest(days);
    }

    private void insertConfirmedLunch(int servings) {
        long count =
                insertId("INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, aggregated_at,"
                        + " confirmed_at) VALUES (" + campus + ", " + year + ", '" + MONDAY
                        + "', 'LUNCH', 'CONFIRMED', now(), now()) RETURNING id");
        jdbc.update(
                "INSERT INTO meal_count_lines (meal_count_id, classroom_id, pending, present, absent, missing, normal,"
                        + " substitute) VALUES (?, ?, FALSE, ?, 0, 0, ?, 0)",
                count,
                classroom,
                servings,
                servings);
    }

    private long food(
            String code, String name, String storage, String origin, String kcal, String p, String l, String g) {
        return insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, storage, origin) VALUES ('" + code + "', '"
                        + name
                        + "', 'OTHER', 'g', 'kg', 1000, 20000, " + kcal + ", " + p + ", " + l + ", " + g + ", 'TEST', '"
                        + storage + "', '" + origin + "') RETURNING id");
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

    private void campusRole(CurrentUser user, String staffRole) {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) VALUES (?, ?, ?, '2026-01-01')",
                user.userId(),
                staffRole,
                campus);
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
