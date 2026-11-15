package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.MealPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
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

/**
 * Công bố và lưu lưới chạy song song (hai tab, hai người có MENU_MANAGE): công bố phải kiểm đúng lưới sẽ được công bố.
 * Không bọc @Transactional để mỗi luồng tự commit.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MealPlanPublishConcurrencyIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName(
            "NUT-08: lưới bị lưu thành chưa cân đối trong lúc công bố ⇒ không ra tuần PUBLISHED với lưới chưa kiểm")
    void publishNeverPublishesAGridItDidNotCheck() throws Exception {
        long campus = insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-R', 'R', TRUE) RETURNING id");
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        jdbc.update(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'Lớp R', ?)",
                campus,
                year,
                ag3);
        jdbc.update(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,"
                        + " protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max, glucid_pct_min, glucid_pct_max)"
                        + " VALUES (?, '2026-09-01', 30000, 300, 600, 12, 25, 15, 35, 40, 70)",
                ag3);
        long rice = food("GAO-R", "DRY", "PLANT", "350", "7", "1", "77");
        long pork = food("THIT-R", "FRESH", "ANIMAL", "250", "20", "18", "0");
        long milk = food("SUA-R", "FRESH", "ANIMAL", "60", "3", "3", "5");
        long com = dish("COM-R", "CARB", rice, 60, ag3);
        long thit = dish("THIT-R", "MAIN", pork, 40, ag3);
        long sua = dish("SUA-R", "SNACK", milk, 200, ag3);
        long banh = dish("BANH-R", "SNACK", rice, 200, ag3);
        CurrentUser principal = principal();
        // Lưới đã lưu: chiều sữa ⇒ 430 kcal, đạt định mức
        mealPlanService.saveMealPlan(principal, MONDAY, mondayOnly(com, thit, sua));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch saved = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            // Tab khác đổi chiều sang bánh gạo 200 g (1010 kcal > 600) và chưa commit
            Future<?> editor = pool.submit(() -> tx.executeWithoutResult(status -> {
                mealPlanService.saveMealPlan(principal, MONDAY, mondayOnly(com, thit, banh));
                saved.countDown();
                await(release);
            }));
            assertThat(saved.await(30, TimeUnit.SECONDS)).isTrue();
            Future<Throwable> publisher = pool.submit(() -> {
                try {
                    mealPlanService.publishMealPlan(principal, MONDAY);
                    return null;
                } catch (Throwable ex) {
                    return ex;
                }
            });
            try {
                publisher.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException waitingForLock) {
                // Công bố đang chờ lần lưu kia commit — đúng mong đợi
            }
            release.countDown();
            editor.get(30, TimeUnit.SECONDS);
            Throwable outcome = publisher.get(30, TimeUnit.SECONDS);

            String status = jdbc.queryForObject(
                    "SELECT status FROM meal_plan_weeks WHERE week_start = ?", String.class, MONDAY);
            Long afternoonDish = jdbc.queryForObject(
                    "SELECT i.dish_id FROM meal_plan_items i WHERE i.menu_date = ? AND i.session = 'AFTERNOON'",
                    Long.class,
                    MONDAY);
            assertThat(afternoonDish).isEqualTo(banh);
            assertThat(status).isEqualTo("DRAFT");
            assertThat(outcome)
                    .isInstanceOf(GlobalException.class)
                    .extracting("apiCode")
                    .isEqualTo(ApiCode.MEAL_PLAN_NOT_PUBLISHABLE);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Thứ Hai: trưa cơm + thịt, chiều {@code afternoonDish}; thứ Ba–Sáu nghỉ. */
    private static MealPlanRequest mondayOnly(long com, long thit, long afternoonDish) {
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

    private CurrentUser principal() {
        long id = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name)"
                + " VALUES ('kc-principal-r', 'principal-r', 'principal-r') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                id);
        return new CurrentUser(
                id,
                "kc-principal-r",
                "principal-r",
                "principal-r",
                null,
                Set.of(CareNestRole.PRINCIPAL),
                TestPermissions.defaultsOf(CareNestRole.PRINCIPAL));
    }

    private long food(String code, String storage, String origin, String kcal, String p, String l, String g) {
        return insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, storage, origin) VALUES ('" + code + "', '"
                        + code
                        + "', 'OTHER', 'g', 'kg', 1000, 20000, " + kcal + ", " + p + ", " + l + ", " + g + ", 'TEST', '"
                        + storage + "', '" + origin + "') RETURNING id");
    }

    private long dish(String code, String type, long foodId, int grams, long ageGroupId) {
        long id = insertId("INSERT INTO dishes (code, name, dish_type) VALUES ('" + code + "', '" + code + "', '" + type
                + "') RETURNING id");
        jdbc.update(
                "INSERT INTO dish_ingredients (dish_id, food_id, age_group_id, grams_per_child) VALUES (?, ?, ?, ?)",
                id,
                foodId,
                ageGroupId,
                grams);
        return id;
    }

    private long insertId(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
