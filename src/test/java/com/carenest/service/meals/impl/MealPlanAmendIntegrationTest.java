package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.meals.MealPlanAmendRequest;
import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MealPlanRevisionResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.MealPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** Sửa ngoại lệ một ngày của thực đơn tuần đã công bố (NUT-08, P-39). Giờ chốt mặc định 08:45 giờ trường. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class MealPlanAmendIntegrationTest {

    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);
    private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
    private static final LocalDate THURSDAY = MONDAY.plusDays(3);
    private static final String REASON = "Nhà cung cấp báo hết thịt heo";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser principal;
    private CurrentUser vp;
    private CurrentUser kitchen;
    private long campus;
    private long year;
    private long classroom;
    private long ag3;
    private long rice;
    private long com;
    private long thit;
    private long sua;
    private long banh;

    @BeforeEach
    void setUp() {
        // Hôm nay = thứ Ba 10/11, 07:00 (trước giờ chốt)
        clock.set(TUESDAY, "07:00");
        campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-A', 'Cơ sở A', TRUE) RETURNING id");
        ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        classroom = insertId("INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (" + campus
                + ", " + year + ", 'Lớp A', " + ag3 + ") RETURNING id");
        jdbc.update(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,"
                        + " protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max, glucid_pct_min, glucid_pct_max)"
                        + " VALUES (?, '2026-09-01', 30000, 300, 600, 12, 25, 15, 35, 40, 70)",
                ag3);
        rice = food("GAO", "Gạo", "DRY", "PLANT", "350", "7", "1", "77");
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
    @DisplayName("P-39: sửa ngày mai ⇒ tuần vẫn công bố, có lịch sử trước/sau, lý do, người sửa; bếp được báo")
    void amendFutureDayKeepsPublishedAndRecordsHistory() {
        publishWeek();

        MealPlanResponse amended =
                mealPlanService.amendMealPlanDay(principal, MONDAY, WEDNESDAY, lunch("Đổi món", com));

        assertThat(amended.status()).isEqualTo("PUBLISHED");
        assertThat(amended.revisionCount()).isEqualTo(1);
        assertThat(day(amended, WEDNESDAY).items())
                .extracting(MealPlanResponse.Item::dishId)
                .containsExactly(com);
        assertThat(day(amended, WEDNESDAY).note()).isEqualTo("Đổi món");
        List<MealPlanRevisionResponse> revisions = mealPlanService.getMealPlanRevisions(vp, MONDAY);
        assertThat(revisions).singleElement().satisfies(r -> {
            assertThat(r.menuDate()).isEqualTo(WEDNESDAY);
            assertThat(r.reason()).isEqualTo(REASON);
            assertThat(r.changedBy()).isEqualTo(principal.userId());
            assertThat(r.changedByName()).isEqualTo("principal");
            assertThat(r.before().items())
                    .extracting(MealPlanRevisionResponse.RevisionItem::dishName)
                    .containsExactly("Cơm", "Thịt rim", "Sữa");
            assertThat(r.after().items())
                    .extracting(MealPlanRevisionResponse.RevisionItem::dishName)
                    .containsExactly("Cơm");
            assertThat(r.after().note()).isEqualTo("Đổi món");
        });
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notifications WHERE type = 'MENU_CHANGED' AND user_id = ?",
                        Integer.class,
                        kitchen.userId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("P-39: chỉ cảnh báo cân đối, không chặn; cảnh báo lưu vào lịch sử")
    void unbalancedAmendIsSavedWithIssues() {
        publishWeek();

        // Bữa chiều bánh gạo 200 g ⇒ vượt 600 kcal
        mealPlanService.amendMealPlanDay(
                principal,
                MONDAY,
                WEDNESDAY,
                new MealPlanAmendRequest(
                        "",
                        List.of(
                                item(MealSession.LUNCH, com),
                                item(MealSession.LUNCH, thit),
                                item(MealSession.AFTERNOON, banh)),
                        REASON));

        assertThat(mealPlanService.getMealPlanRevisions(principal, MONDAY))
                .singleElement()
                .satisfies(r -> assertThat(r.issues())
                        .extracting(MealPlanRevisionResponse.RevisionIssue::code)
                        .contains("NOT_BALANCED"));
    }

    @Test
    @DisplayName("P-39: hôm nay trước giờ chốt sửa được; từ giờ chốt, ngày đã qua, ngày nghỉ bị khóa")
    void lockedByCutoffPastAndOffDays() {
        publishWeek();

        MealPlanResponse view = mealPlanService.getMealPlan(principal, MONDAY);
        assertThat(view.days())
                .extracting(MealPlanResponse.Day::canAmend)
                .containsExactly(false, true, true, false, false);
        mealPlanService.amendMealPlanDay(principal, MONDAY, TUESDAY, lunch("", com));

        clock.set(TUESDAY, "08:45");
        assertThat(day(mealPlanService.getMealPlan(principal, MONDAY), TUESDAY).canAmend())
                .isFalse();
        expectCode(
                () -> mealPlanService.amendMealPlanDay(principal, MONDAY, TUESDAY, lunch("", thit)),
                ApiCode.MEAL_PLAN_DAY_LOCKED);
        expectCode(
                () -> mealPlanService.amendMealPlanDay(principal, MONDAY, MONDAY, lunch("", thit)),
                ApiCode.MEAL_PLAN_DAY_LOCKED);
        expectCode(
                () -> mealPlanService.amendMealPlanDay(principal, MONDAY, THURSDAY, lunch("", thit)),
                ApiCode.MEAL_PLAN_DAY_LOCKED);
    }

    @Test
    @DisplayName("P-39: tuần nháp, thiếu quyền, thiếu lý do, không đổi gì, món ngừng dùng, ngày ngoài tuần bị từ chối")
    void rejectsInvalidAmend() {
        mealPlanService.saveMealPlan(principal, MONDAY, weekPlan());
        expectCode(
                () -> mealPlanService.amendMealPlanDay(principal, MONDAY, WEDNESDAY, lunch("", com)),
                ApiCode.MEAL_PLAN_NOT_PUBLISHED);
        mealPlanService.publishMealPlan(principal, MONDAY);

        expectCode(() -> mealPlanService.amendMealPlanDay(vp, MONDAY, WEDNESDAY, lunch("", com)), ApiCode.FORBIDDEN);
        expectCode(
                () -> mealPlanService.amendMealPlanDay(
                        principal,
                        MONDAY,
                        WEDNESDAY,
                        new MealPlanAmendRequest("", List.of(item(MealSession.LUNCH, com)), " hết hàng ")),
                ApiCode.MEAL_PLAN_REASON_REQUIRED);
        expectCode(
                () -> mealPlanService.amendMealPlanDay(
                        principal,
                        MONDAY,
                        WEDNESDAY,
                        new MealPlanAmendRequest(
                                "",
                                List.of(
                                        item(MealSession.LUNCH, com),
                                        item(MealSession.LUNCH, thit),
                                        item(MealSession.AFTERNOON, sua)),
                                REASON)),
                ApiCode.MEAL_PLAN_AMEND_NO_CHANGE);
        jdbc.update("UPDATE dishes SET status = 'INACTIVE' WHERE id = ?", banh);
        expectCode(
                () -> mealPlanService.amendMealPlanDay(principal, MONDAY, WEDNESDAY, lunch("", banh)),
                ApiCode.MEAL_PLAN_DISH_INACTIVE);
        expectCode(
                () -> mealPlanService.amendMealPlanDay(principal, MONDAY, MONDAY.plusDays(5), lunch("", com)),
                ApiCode.MEAL_PLAN_DATE_INVALID);
        assertThat(mealPlanService.getMealPlanRevisions(principal, MONDAY)).isEmpty();
    }

    @Test
    @DisplayName("P-38, P-39: ngày đã xác nhận suất ⇒ sửa thực đơn tính lại nhu cầu kho khô theo món mới")
    void amendResyncsConfirmedDayStock() {
        insertConfirmedLunch(TUESDAY, 10);
        publishWeek();
        // Trưa cơm 60 g gạo × 10 suất
        assertThat(riceDemand(TUESDAY)).isEqualByComparingTo("600");

        mealPlanService.amendMealPlanDay(principal, MONDAY, TUESDAY, lunch("", thit));

        assertThat(riceDemand(TUESDAY)).isEqualByComparingTo("0");
    }

    private void publishWeek() {
        mealPlanService.saveMealPlan(principal, MONDAY, weekPlan());
        mealPlanService.publishMealPlan(principal, MONDAY);
    }

    /** Thứ Hai–Tư: trưa cơm + thịt, chiều sữa (đạt cân đối); thứ Năm–Sáu nghỉ. */
    private MealPlanRequest weekPlan() {
        List<MealPlanRequest.DayInput> days = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            days.add(new MealPlanRequest.DayInput(
                    MONDAY.plusDays(i),
                    false,
                    "",
                    List.of(
                            item(MealSession.LUNCH, com),
                            item(MealSession.LUNCH, thit),
                            item(MealSession.AFTERNOON, sua))));
        }
        for (int i = 3; i < 5; i++) {
            days.add(new MealPlanRequest.DayInput(MONDAY.plusDays(i), true, "Nghỉ", List.of()));
        }
        return new MealPlanRequest(days);
    }

    private static MealPlanAmendRequest lunch(String note, long dishId) {
        return new MealPlanAmendRequest(note, List.of(item(MealSession.LUNCH, dishId)), REASON);
    }

    private static MealPlanRequest.ItemInput item(MealSession session, long dishId) {
        return new MealPlanRequest.ItemInput(session, MenuBlock.KINDERGARTEN, dishId);
    }

    private static MealPlanResponse.Day day(MealPlanResponse plan, LocalDate date) {
        return plan.days().stream()
                .filter(d -> d.date().equals(date))
                .findFirst()
                .orElseThrow();
    }

    private BigDecimal riceDemand(LocalDate date) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(required_qty), 0) FROM stock_daily_demands WHERE campus_id = ? AND meal_date = ?"
                        + " AND food_id = ?",
                BigDecimal.class,
                campus,
                date,
                rice);
    }

    private void insertConfirmedLunch(LocalDate date, int servings) {
        long count =
                insertId("INSERT INTO meal_counts (campus_id, school_year_id, date, session, status, aggregated_at,"
                        + " confirmed_at) VALUES (" + campus + ", " + year + ", '" + date
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

    /** Đồng hồ giờ trường chỉnh được trong test để kiểm trước/sau giờ chốt. */
    static class MutableClock extends Clock {
        private Instant instant = Instant.now();

        void set(LocalDate date, String time) {
            instant = date.atTime(java.time.LocalTime.parse(time))
                    .atZone(SCHOOL_ZONE)
                    .toInstant();
        }

        @Override
        public ZoneId getZone() {
            return SCHOOL_ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
