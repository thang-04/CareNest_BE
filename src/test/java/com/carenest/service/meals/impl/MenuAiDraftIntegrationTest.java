package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MenuAiDraftRequest;
import com.carenest.dto.meals.MenuAiDraftResponse;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiGateway;
import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.AiRawResult;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.MealPlanService;
import com.carenest.service.meals.MenuAiDraftService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AiDataClass;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftKind;
import com.carenest.utils.enums.MenuAiDraftSource;
import com.carenest.utils.enums.MenuAiDraftStatus;
import com.carenest.utils.enums.MenuAiFallbackReason;
import com.carenest.utils.enums.MenuBlock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Nháp gợi ý thực đơn tuần end-to-end trên PostgreSQL thật (AC-1, AC-5, AC-6, AC-7). Không {@code @Transactional}: tạo
 * nháp chạy ngoài transaction (chờ model).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MenuAiDraftIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MenuAiDraftService draftService;

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AiGateway aiGateway;

    private CurrentUser principal;
    private CurrentUser vp;
    private long ag3;
    private long pork;
    private long com;
    private long thit;

    @BeforeEach
    void setUp() {
        // Nháp tạo ngoài transaction test nên dữ liệu phải commit: danh mục seed một lần cho container, mỗi test chỉ
        // dọn nháp và kế hoạch tuần
        jdbc.update("DELETE FROM menu_ai_drafts");
        jdbc.update("DELETE FROM meal_plan_weeks");
        ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        if (jdbc.queryForObject("SELECT count(*) FROM campuses WHERE code = 'CS-AI'", Long.class) == 0) {
            seedCatalog();
        }
        pork = jdbc.queryForObject("SELECT id FROM foods WHERE code = 'AI-THIT'", Long.class);
        jdbc.update("UPDATE foods SET kcal = 250 WHERE id = ?", pork);
        com = jdbc.queryForObject("SELECT id FROM dishes WHERE code = 'AI-COM'", Long.class);
        thit = jdbc.queryForObject("SELECT id FROM dishes WHERE code = 'AI-THITRIM'", Long.class);
        principal = user("ai-principal", CareNestRole.PRINCIPAL);
        vp = user("ai-vp", CareNestRole.VICE_PRINCIPAL);
    }

    private void seedCatalog() {
        long campus =
                insertId("INSERT INTO campuses (code, name, is_main) VALUES ('CS-AI', 'Cơ sở AI', TRUE) RETURNING id");
        long year = insertId("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        jdbc.update(
                "INSERT INTO classrooms (campus_id, school_year_id, name, age_group_id) VALUES (?, ?, 'Lớp AI', ?)",
                campus,
                year,
                ag3);
        jdbc.update(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,"
                        + " protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max, glucid_pct_min, glucid_pct_max)"
                        + " VALUES (?, '2026-09-01', 30000, 300, 600, 12, 25, 15, 35, 40, 70)",
                ag3);
        long rice = food("AI-GAO", "Gạo (giả)", "PLANT", "350", "7", "1", "77");
        long porkFood = food("AI-THIT", "Thịt (giả)", "ANIMAL", "250", "20", "18", "0");
        long fish = food("AI-CA", "Cá (giả)", "ANIMAL", "100", "18", "3", "0");
        long tofu = food("AI-DAU", "Đậu phụ (giả)", "PLANT", "95", "11", "5", "1");
        long milk = food("AI-SUA", "Sữa (giả)", "ANIMAL", "60", "3", "3", "5");
        long rice60 = dish("AI-COM", "Cơm (giả)", "CARB", rice, 60);
        long porkDish = dish("AI-THITRIM", "Thịt rim (giả)", "MAIN", porkFood, 40);
        dish("AI-CAHAP", "Cá hấp (giả)", "MAIN", fish, 60);
        dish("AI-DAUSOT", "Đậu sốt (giả)", "MAIN", tofu, 80);
        long milkDish = dish("AI-SUACHUA", "Sữa (giả)", "SNACK", milk, 200);
        // Bộ mẫu mẫu giáo: trưa cơm + mặn, chiều một món phụ ⇒ khung ô của gợi ý
        long set = insertId("INSERT INTO menu_template_sets (name, season_label, menu_block) "
                + "VALUES ('Mẫu (giả)', 'Mùa đông', 'KINDERGARTEN') RETURNING id");
        templateItem(set, "LUNCH", rice60, 0);
        templateItem(set, "LUNCH", porkDish, 1);
        templateItem(set, "AFTERNOON", milkDish, 0);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                userId("ai-principal"));
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from)"
                        + " VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                userId("ai-vp"),
                campus);
    }

    @Test
    @DisplayName("AC-1, AC-7: AI tắt ⇒ nháp theo quy tắc, khung ô theo thực đơn mẫu, mọi ngày đạt kiểm, khớp evaluator")
    void aiDisabledGivesRuleBasedDraft() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));

        assertThat(draft.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(draft.source()).isEqualTo(MenuAiDraftSource.RULE_BASED);
        assertThat(draft.fallbackReason()).isEqualTo(MenuAiFallbackReason.AI_DISABLED);
        assertThat(draft.model()).isNull();
        assertThat(draft.score().ai()).isNull();
        assertThat(draft.score().baseline()).isBetween(1, 100);
        assertThat(draft.days()).hasSize(5).allSatisfy(d -> {
            assertThat(d.items()).hasSize(3);
            assertThat(d.items())
                    .filteredOn(i -> i.session() == MealSession.LUNCH)
                    .hasSize(2);
        });
        assertThat(draft.issues()).noneMatch(i -> MealPlanEvaluator.BLOCKING.equals(i.severity()));
        assertThat(draftService.getDraft(principal, draft.id())).isEqualTo(draft);
    }

    @Test
    @DisplayName(
            "Thực phẩm ngừng dùng ⇒ món chứa nó không được gợi ý; bảng kiểm tiêu chí đi kèm nháp, đối chiếu bộ mẫu mùa")
    void inactiveFoodsExcludedAndChecksAttached() {
        jdbc.update("UPDATE foods SET status = 'INACTIVE' WHERE code = 'AI-CA'");
        long fishDish = jdbc.queryForObject("SELECT id FROM dishes WHERE code = 'AI-CAHAP'", Long.class);
        try {
            MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));

            assertThat(draft.days())
                    .flatMap(MenuAiDraftResponse.Day::items)
                    .extracting(MenuAiDraftResponse.Item::dishId)
                    .doesNotContain(fishDish);
            assertThat(draft.checks())
                    .extracting(MenuAiDraftResponse.Check::code)
                    .contains(
                            "NORM_BALANCE",
                            "OVER_BUDGET",
                            "DISH_REPEATED",
                            "HISTORY",
                            "SEASON",
                            "CATALOG",
                            "VALUE_GATE");
            assertThat(draft.checks())
                    .filteredOn(c -> "SEASON".equals(c.code()))
                    .singleElement()
                    .satisfies(c -> assertThat(c.detail()).contains("Mùa đông"));
            assertThat(draftService.getDraft(principal, draft.id()).checks()).isEqualTo(draft.checks());
        } finally {
            jdbc.update("UPDATE foods SET status = 'ACTIVE' WHERE code = 'AI-CA'");
        }
    }

    @Test
    @DisplayName("AC-5: dùng nháp ⇒ chỉ đổi khối mẫu giáo, giữ khối nhà trẻ, tuần vẫn nháp; dùng lần hai không ghi lại")
    void applyReplacesOnlyDraftBlockAndIsIdempotent() {
        mealPlanService.saveMealPlan(principal, MONDAY, nurseryLunchOnMonday());
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));

        MealPlanResponse applied = draftService.applyDraft(principal, draft.id());

        assertThat(applied.status()).isEqualTo("DRAFT");
        assertThat(applied.days().getFirst().items())
                .filteredOn(i -> i.block() == MenuBlock.NURSERY)
                .singleElement()
                .satisfies(i -> assertThat(i.dishId()).isEqualTo(com));
        assertThat(applied.days())
                .allSatisfy(d -> assertThat(d.items())
                        .filteredOn(i -> i.block() == MenuBlock.KINDERGARTEN)
                        .hasSize(3));
        assertThat(draftService.getDraft(principal, draft.id()).status()).isEqualTo(MenuAiDraftStatus.APPLIED);
        Long itemsBefore = jdbc.queryForObject("SELECT count(*) FROM meal_plan_items", Long.class);

        MealPlanResponse again = draftService.applyDraft(principal, draft.id());

        assertThat(again.days()).isEqualTo(applied.days());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_plan_items", Long.class))
                .isEqualTo(itemsBefore);
    }

    @Test
    @DisplayName("AC-6: tuần đã công bố ⇒ không tạo, không dùng nháp (409)")
    void publishedWeekRejectsDrafts() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));
        draftService.applyDraft(principal, draft.id());
        MenuAiDraftResponse second = draftService.createDraft(principal, weekly(MONDAY));
        mealPlanService.publishMealPlan(principal, MONDAY);

        expectCode(() -> draftService.createDraft(principal, weekly(MONDAY)), ApiCode.MEAL_PLAN_PUBLISHED);
        expectCode(() -> draftService.applyDraft(principal, second.id()), ApiCode.MEAL_PLAN_PUBLISHED);
    }

    @Test
    @DisplayName("AC-6: thực phẩm đổi sau khi tạo nháp làm phát sinh lỗi chặn ⇒ dùng nháp 409, tuần không đổi")
    void staleDraftIsRejected() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));
        jdbc.update("UPDATE foods SET kcal = 2500, updated_at = now() + interval '1 minute' WHERE id = ?", pork);

        assertThatThrownBy(() -> draftService.applyDraft(principal, draft.id()))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> {
                    GlobalException ge = (GlobalException) ex;
                    assertThat(ge.getApiCode()).isEqualTo(ApiCode.MENU_AI_DRAFT_STALE);
                    assertThat(ge.getFieldErrors()).isNotEmpty();
                });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_plan_weeks", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("Bỏ nháp ⇒ DISMISSED; bỏ lần hai hoặc dùng sau khi bỏ ⇒ 409; id lạ ⇒ 404")
    void dismissedDraftCannotBeUsed() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));

        assertThat(draftService.dismissDraft(principal, draft.id()).status()).isEqualTo(MenuAiDraftStatus.DISMISSED);
        expectCode(() -> draftService.dismissDraft(principal, draft.id()), ApiCode.MENU_AI_DRAFT_NOT_PENDING);
        expectCode(() -> draftService.applyDraft(principal, draft.id()), ApiCode.MENU_AI_DRAFT_NOT_PENDING);
        expectCode(() -> draftService.getDraft(principal, -1L), ApiCode.MENU_AI_DRAFT_NOT_FOUND);
    }

    @Test
    @DisplayName("AUTH-20: PHT chưa được cấp MENU_MANAGE không tạo/xem nháp; tuần không phải thứ Hai ⇒ 400")
    void requiresMenuManageAndMonday() {
        expectCode(() -> draftService.createDraft(vp, weekly(MONDAY)), ApiCode.FORBIDDEN);
        expectCode(
                () -> draftService.createDraft(principal, weekly(MONDAY.plusDays(1))), ApiCode.MEAL_PLAN_WEEK_INVALID);
        expectCode(
                () -> draftService.createDraft(
                        principal,
                        new MenuAiDraftRequest(
                                MenuAiDraftKind.MENU_DAY_FIX,
                                MenuBlock.KINDERGARTEN,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)),
                ApiCode.INVALID_REQUEST_DATA);
    }

    @Test
    @DisplayName("AI-05, AC-2: output model không dùng được ⇒ AI_INVALID; bảng nháp không lưu response thô")
    void rawModelOutputIsNeverStored() {
        given(aiGateway.isAvailable(any())).willReturn(true);
        given(aiGateway.taskConfig(any()))
                .willReturn(Optional.of(new AiProperties.TaskConfig(
                        List.of("fake"), AiDataClass.PUBLIC_CATALOG, Duration.ofSeconds(30), 1)));
        given(aiGateway.complete(any()))
                .willReturn(new AiRawResult("{\"note\":\"RAW-MARKER-7f3\"}", "fake", "fake-model", null, 1, 1, 3));

        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));

        assertThat(draft.source()).isEqualTo(MenuAiDraftSource.RULE_BASED);
        assertThat(draft.fallbackReason()).isEqualTo(MenuAiFallbackReason.AI_INVALID);
        assertThat(draft.model()).isEqualTo("fake-model");
        String stored = jdbc.queryForObject(
                "SELECT request_json::text || output_json::text || score_json::text || issues_json::text"
                        + " FROM menu_ai_drafts WHERE id = ?",
                String.class,
                draft.id());
        assertThat(stored).doesNotContain("RAW-MARKER").doesNotContain("Bạn là trợ lý");
    }

    @Test
    @DisplayName("AC-9: mẫu ngày ⇒ một ngày đủ ô, không gắn ngày; không ghi vào kế hoạch tuần, chỉ đánh dấu đã dùng")
    void dailyDraftIsMarkedUsedNotApplied() {
        MenuAiDraftResponse daily = draftService.createDraft(principal, daily());

        assertThat(daily.kind()).isEqualTo(MenuAiDraftKind.MENU_DAILY);
        assertThat(daily.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(daily.weekStart()).isNull();
        assertThat(daily.days()).singleElement().satisfies(d -> {
            assertThat(d.date()).isNull();
            assertThat(d.items()).hasSize(3);
        });
        expectCode(() -> draftService.applyDraft(principal, daily.id()), ApiCode.MENU_AI_DRAFT_KIND_INVALID);

        assertThat(draftService.markDailyUsed(principal, daily.id()).status()).isEqualTo(MenuAiDraftStatus.APPLIED);
        assertThat(draftService.markDailyUsed(principal, daily.id()).status()).isEqualTo(MenuAiDraftStatus.APPLIED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_plan_weeks", Long.class))
                .isZero();
        MenuAiDraftResponse weekly = draftService.createDraft(principal, weekly(MONDAY));
        expectCode(() -> draftService.markDailyUsed(principal, weekly.id()), ApiCode.MENU_AI_DRAFT_KIND_INVALID);
    }

    @Test
    @DisplayName("AC-9: thay một món ⇒ chỉ đổi đúng ô của tuần nháp, món cùng loại, ô khác và khối khác giữ nguyên")
    void dayFixReplacesOnlyTargetCell() {
        mealPlanService.saveMealPlan(principal, MONDAY, nurseryLunchOnMonday());
        draftService.applyDraft(
                principal, draftService.createDraft(principal, weekly(MONDAY)).id());
        MealPlanResponse before = mealPlanService.getMealPlan(principal, MONDAY);
        long mondayMain = mainOf(before, MONDAY);

        MenuAiDraftResponse fix = draftService.createDraft(principal, dayFix(MONDAY, mondayMain));

        assertThat(fix.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(fix.targetDate()).isEqualTo(MONDAY);
        assertThat(fix.fromDishId()).isEqualTo(mondayMain);
        MenuAiDraftResponse.Item to = fix.days().getFirst().items().getFirst();
        assertThat(to.dishId()).isNotEqualTo(mondayMain);
        assertThat(to.dishType()).isEqualTo(DishType.MAIN);

        MealPlanResponse after = draftService.applyDraft(principal, fix.id());

        assertThat(mainOf(after, MONDAY)).isEqualTo(to.dishId());
        assertThat(after.days().subList(1, 5)).isEqualTo(before.days().subList(1, 5));
        assertThat(after.days().getFirst().items())
                .filteredOn(i -> i.block() == MenuBlock.NURSERY)
                .singleElement()
                .satisfies(i -> assertThat(i.dishId()).isEqualTo(com));
        // Dùng lần hai không ghi lại: cùng tập món mỗi ngày (thứ tự đọc lại từ DB có thể khác thứ tự vừa lưu)
        assertThat(cellsOf(draftService.applyDraft(principal, fix.id()))).isEqualTo(cellsOf(after));
    }

    private static List<Set<String>> cellsOf(MealPlanResponse plan) {
        return plan.days().stream()
                .map(d -> d.items().stream()
                        .map(i -> i.session() + "|" + i.block() + "|" + i.dishId())
                        .collect(Collectors.toSet()))
                .toList();
    }

    @Test
    @DisplayName("Thay một món ở tuần mới lập một ngày: chỉ xét lỗi chặn của ngày cần thay, ngày trống không cản")
    void dayFixOnPartialWeekChecksOnlyTargetDay() {
        mealPlanService.saveMealPlan(principal, MONDAY, kindergartenMondayOnly());

        MenuAiDraftResponse fix = draftService.createDraft(principal, dayFix(MONDAY, thit));

        assertThat(fix.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(fix.issues())
                .filteredOn(i -> MealPlanEvaluator.BLOCKING.equals(i.severity()))
                .isEmpty();
        assertThat(fix.checks())
                .filteredOn(c -> "NORM_BALANCE".equals(c.code()))
                .singleElement()
                .satisfies(c -> assertThat(c.detail()).startsWith("1/1"));
    }

    @Test
    @DisplayName("AC-9: món cần thay đã bị sửa tay sau khi tạo gợi ý ⇒ dùng gợi ý 409, tuần không đổi")
    void dayFixRejectedWhenCellChanged() {
        mealPlanService.saveMealPlan(principal, MONDAY, kindergartenMondayOnly());
        MenuAiDraftResponse fix = draftService.createDraft(principal, dayFix(MONDAY, thit));
        long otherMain = fix.days().getFirst().items().getFirst().dishId();
        mealPlanService.saveMealPlan(principal, MONDAY, kindergartenMonday(otherMain));

        expectCode(() -> draftService.applyDraft(principal, fix.id()), ApiCode.MENU_AI_DRAFT_STALE);
        assertThat(mainOf(mealPlanService.getMealPlan(principal, MONDAY), MONDAY))
                .isEqualTo(otherMain);
    }

    private static long mainOf(MealPlanResponse plan, LocalDate date) {
        return plan.days().stream()
                .filter(d -> d.date().equals(date))
                .flatMap(d -> d.items().stream())
                .filter(i -> i.block() == MenuBlock.KINDERGARTEN && i.dishType() == DishType.MAIN)
                .findFirst()
                .orElseThrow()
                .dishId();
    }

    private MealPlanRequest kindergartenMondayOnly() {
        return kindergartenMonday(thit);
    }

    private MealPlanRequest kindergartenMonday(long main) {
        long sua = jdbc.queryForObject("SELECT id FROM dishes WHERE code = 'AI-SUACHUA'", Long.class);
        return new MealPlanRequest(List.of(new MealPlanRequest.DayInput(
                MONDAY,
                false,
                "",
                List.of(
                        new MealPlanRequest.ItemInput(MealSession.LUNCH, MenuBlock.KINDERGARTEN, com),
                        new MealPlanRequest.ItemInput(MealSession.LUNCH, MenuBlock.KINDERGARTEN, main),
                        new MealPlanRequest.ItemInput(MealSession.AFTERNOON, MenuBlock.KINDERGARTEN, sua)))));
    }

    private static MenuAiDraftRequest daily() {
        return new MenuAiDraftRequest(
                MenuAiDraftKind.MENU_DAILY, MenuBlock.KINDERGARTEN, null, "Mùa đông", null, null, null, null);
    }

    private static MenuAiDraftRequest dayFix(LocalDate date, long fromDish) {
        return new MenuAiDraftRequest(
                MenuAiDraftKind.MENU_DAY_FIX,
                MenuBlock.KINDERGARTEN,
                MONDAY,
                null,
                null,
                date,
                MealSession.LUNCH,
                fromDish);
    }

    private static MenuAiDraftRequest weekly(LocalDate weekStart) {
        return new MenuAiDraftRequest(
                MenuAiDraftKind.MENU_WEEKLY,
                MenuBlock.KINDERGARTEN,
                weekStart,
                "Mùa đông",
                "nhiều rau",
                null,
                null,
                null);
    }

    private MealPlanRequest nurseryLunchOnMonday() {
        List<MealPlanRequest.DayInput> days = new ArrayList<>();
        days.add(new MealPlanRequest.DayInput(
                MONDAY, false, "", List.of(new MealPlanRequest.ItemInput(MealSession.LUNCH, MenuBlock.NURSERY, com))));
        return new MealPlanRequest(days);
    }

    private long food(String code, String name, String origin, String kcal, String p, String l, String g) {
        return insertId(
                "INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,"
                        + " kcal, protein, lipid, glucid, nutrient_source, storage, origin) VALUES ('" + code + "', '"
                        + name + "', 'OTHER', 'g', 'kg', 1000, 20000, " + kcal + ", " + p + ", " + l + ", " + g
                        + ", 'TEST', 'FRESH', '" + origin + "') RETURNING id");
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

    private void templateItem(long set, String session, long dishId, int sortOrder) {
        jdbc.update(
                "INSERT INTO menu_template_items (set_id, week_no, weekday, session, dish_id, sort_order)"
                        + " VALUES (?, 1, 1, ?, ?, ?)",
                set,
                session,
                dishId,
                sortOrder);
    }

    private long userId(String username) {
        return insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + username
                + "', '" + username + "', '" + username + "') RETURNING id");
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = jdbc.queryForObject("SELECT id FROM user_accounts WHERE username = ?", Long.class, username);
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
