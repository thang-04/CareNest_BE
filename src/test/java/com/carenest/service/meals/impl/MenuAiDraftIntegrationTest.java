package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MenuAiDraftCandidatesResponse;
import com.carenest.dto.meals.MenuAiDraftEvaluationResponse;
import com.carenest.dto.meals.MenuAiDraftGridRequest;
import com.carenest.dto.meals.MenuAiDraftRequest;
import com.carenest.dto.meals.MenuAiDraftResponse;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.AiGateway;
import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.AiRawResult;
import com.carenest.integration.ai.EmbeddingClient;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.DayBalanceService;
import com.carenest.service.meals.MealPlanService;
import com.carenest.service.meals.MenuAiDraftService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AiDataClass;
import com.carenest.utils.enums.AiErrorReason;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftKind;
import com.carenest.utils.enums.MenuAiDraftSource;
import com.carenest.utils.enums.MenuAiDraftStatus;
import com.carenest.utils.enums.MenuAiFallbackReason;
import com.carenest.utils.enums.MenuAiStrategy;
import com.carenest.utils.enums.MenuBlock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Đồng hồ giờ trường chỉnh được. Mặc định cố định giữa tuần {@link #MONDAY} (không theo giờ thật): tuần test cố định
     * nên chạy theo giờ thật thì sau tuần đó mọi thao tác bị chặn "tuần đã qua".
     */
    static class MutableClock extends Clock {
        private static final Instant DEFAULT =
                MONDAY.plusDays(3).atTime(10, 0).atZone(SCHOOL_ZONE).toInstant();
        private Instant instant = DEFAULT;

        void reset() {
            instant = DEFAULT;
        }

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

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MutableClock clock;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private MenuAiDraftService draftService;

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private DayBalanceService dayBalanceService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AiGateway aiGateway;

    // Mặc định mock báo không khả dụng ⇒ mọi test cũ chạy y như khi embedding tắt
    @MockitoBean
    private EmbeddingClient embeddingClient;

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
        clock.reset();
        jdbc.update("DELETE FROM menu_ai_drafts");
        jdbc.update("DELETE FROM meal_plan_day_revisions");
        jdbc.update("DELETE FROM meal_plan_weeks");
        jdbc.update("DELETE FROM dish_embeddings");
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
        jdbc.update("UPDATE age_group_meal_configs SET budget_tolerance_pct = 100");
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
        assertThat(draft.feasible()).isTrue();
        assertThat(draftService.getDraft(principal, draft.id())).isEqualTo(draft);
    }

    @Test
    @DisplayName(
            "AC-4: danh mục không ghép được tuần đạt định mức ⇒ vẫn có lưới món, feasible=false; dùng được, không công bố được")
    void unmetPlanStillOffersDishesButCannotBePublished() {
        String setKcal = "UPDATE age_group_meal_configs SET kcal_min = ?, kcal_max = ?"
                + " WHERE age_group_id = ? AND effective_from = '2026-09-01'";
        jdbc.update(setKcal, 1500, 1800, ag3);
        try {
            MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));

            assertThat(draft.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
            assertThat(draft.source()).isEqualTo(MenuAiDraftSource.RULE_BASED);
            assertThat(draft.feasible()).isFalse();
            assertThat(draft.days())
                    .hasSize(5)
                    .allSatisfy(d -> assertThat(d.items()).hasSize(3));
            assertThat(draft.issues()).anyMatch(i -> MealPlanEvaluator.BLOCKING.equals(i.severity()));
            assertThat(draft.checks())
                    .filteredOn(c -> "NORM_BALANCE".equals(c.code()))
                    .singleElement()
                    .satisfies(c -> assertThat(c.status()).isEqualTo("FAIL"));

            MealPlanResponse applied = draftService.applyDraft(principal, draft.id(), null, null);

            assertThat(applied.status()).isEqualTo("DRAFT");
            assertThat(applied.days()).allSatisfy(d -> assertThat(d.items()).hasSize(3));
            // NUT-09: thực đơn chưa đạt định mức vẫn không công bố được
            expectCode(() -> mealPlanService.publishMealPlan(principal, MONDAY), ApiCode.MEAL_PLAN_NOT_PUBLISHABLE);
        } finally {
            jdbc.update(setKcal, 300, 600, ag3);
        }
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

        MealPlanResponse applied = draftService.applyDraft(principal, draft.id(), null, null);

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

        MealPlanResponse again = draftService.applyDraft(principal, draft.id(), null, null);

        assertThat(again.days()).isEqualTo(applied.days());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_plan_items", Long.class))
                .isEqualTo(itemsBefore);
    }

    @Test
    @DisplayName("AC-6: tuần đã công bố ⇒ không tạo, không dùng nháp (409)")
    void publishedWeekRejectsDrafts() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));
        draftService.applyDraft(principal, draft.id(), null, null);
        MenuAiDraftResponse second = draftService.createDraft(principal, weekly(MONDAY));
        BalancedWeek.balance(dayBalanceService, principal, MONDAY);
        mealPlanService.publishMealPlan(principal, MONDAY);

        expectCode(() -> draftService.createDraft(principal, weekly(MONDAY)), ApiCode.MEAL_PLAN_PUBLISHED);
        expectCode(() -> draftService.applyDraft(principal, second.id(), null, null), ApiCode.MEAL_PLAN_PUBLISHED);
    }

    @Test
    @DisplayName("AC-6: thực phẩm đổi sau khi tạo nháp làm phát sinh lỗi chặn ⇒ dùng nháp 409, tuần không đổi")
    void staleDraftIsRejected() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));
        jdbc.update("UPDATE foods SET kcal = 2500, updated_at = now() + interval '1 minute' WHERE id = ?", pork);

        assertThatThrownBy(() -> draftService.applyDraft(principal, draft.id(), null, null))
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
        expectCode(() -> draftService.applyDraft(principal, draft.id(), null, null), ApiCode.MENU_AI_DRAFT_NOT_PENDING);
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
        expectCode(
                () -> draftService.applyDraft(principal, daily.id(), null, null), ApiCode.MENU_AI_DRAFT_KIND_INVALID);

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
                principal, draftService.createDraft(principal, weekly(MONDAY)).id(), null, null);
        MealPlanResponse before = mealPlanService.getMealPlan(principal, MONDAY);
        long mondayMain = mainOf(before, MONDAY);

        MenuAiDraftResponse fix = draftService.createDraft(principal, dayFix(MONDAY, mondayMain));

        assertThat(fix.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(fix.targetDate()).isEqualTo(MONDAY);
        assertThat(fix.fromDishId()).isEqualTo(mondayMain);
        MenuAiDraftResponse.Item to = fix.days().getFirst().items().getFirst();
        assertThat(to.dishId()).isNotEqualTo(mondayMain);
        assertThat(to.dishType()).isEqualTo(DishType.MAIN);

        MealPlanResponse after = draftService.applyDraft(principal, fix.id(), null, null);

        assertThat(mainOf(after, MONDAY)).isEqualTo(to.dishId());
        assertThat(after.days().subList(1, 5)).isEqualTo(before.days().subList(1, 5));
        assertThat(after.days().getFirst().items())
                .filteredOn(i -> i.block() == MenuBlock.NURSERY)
                .singleElement()
                .satisfies(i -> assertThat(i.dishId()).isEqualTo(com));
        // Dùng lần hai không ghi lại: cùng tập món mỗi ngày (thứ tự đọc lại từ DB có thể khác thứ tự vừa lưu)
        assertThat(cellsOf(draftService.applyDraft(principal, fix.id(), null, null)))
                .isEqualTo(cellsOf(after));
    }

    private static List<Set<String>> cellsOf(MealPlanResponse plan) {
        return plan.days().stream()
                .map(d -> d.items().stream()
                        .map(i -> i.session() + "|" + i.block() + "|" + i.dishId())
                        .collect(Collectors.toSet()))
                .toList();
    }

    @Test
    @DisplayName("Bảng gợi ý: món mang khóa ô, hàng theo bữa rồi thứ tự ô; ứng viên theo ô chỉ có món đúng loại")
    void draftExposesSlotsAndCandidates() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));

        assertThat(draft.slots())
                .extracting(MenuAiDraftResponse.Slot::key)
                .containsExactly("LUNCH.1", "LUNCH.2", "AFTERNOON.1");
        assertThat(draft.slots().get(1).dishTypes()).containsExactly(DishType.MAIN);
        assertThat(draft.days())
                .allSatisfy(d -> assertThat(d.items())
                        .extracting(MenuAiDraftResponse.Item::slot)
                        .containsExactlyInAnyOrder("LUNCH.1", "LUNCH.2", "AFTERNOON.1"));
        assertThat(draft.editedCells()).isZero();

        MenuAiDraftCandidatesResponse candidates = draftService.getCandidates(principal, draft.id());

        assertThat(candidates.slots())
                .extracting(MenuAiDraftCandidatesResponse.Slot::key)
                .containsExactly("LUNCH.1", "LUNCH.2", "AFTERNOON.1");
        assertThat(mainCandidates(candidates))
                .hasSize(3)
                .allSatisfy(d -> assertThat(d.dishType()).isEqualTo(DishType.MAIN))
                .extracting(MenuAiDraftCandidatesResponse.Dish::dishId)
                .contains(thit);
        expectCode(() -> draftService.getCandidates(vp, draft.id()), ApiCode.FORBIDDEN);
        MenuAiDraftResponse daily = draftService.createDraft(principal, daily());
        expectCode(() -> draftService.getCandidates(principal, daily.id()), ApiCode.MENU_AI_DRAFT_KIND_INVALID);
    }

    @Test
    @DisplayName(
            "Chấm thử: lưới gốc trùng nháp; đổi một ô ⇒ editedCells=1, bảng kiểm ghi người duyệt đã sửa; không lưu")
    void evaluateScoresEditedGridWithoutSaving() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));
        MenuAiDraftGridRequest original = gridOf(draft);

        MenuAiDraftEvaluationResponse same = draftService.evaluateDraft(principal, draft.id(), original);

        assertThat(same.editedCells()).isZero();
        assertThat(same.feasible()).isTrue();
        assertThat(same.score().total()).isEqualTo(draft.score().baseline());
        assertThat(same.issues()).isEqualTo(draft.issues());
        assertThat(same.checks()).isEqualTo(draft.checks());
        assertThat(same.evaluations())
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.block()).isEqualTo(MenuBlock.KINDERGARTEN));

        long other = otherMain(draft, MONDAY);
        MenuAiDraftEvaluationResponse edited =
                draftService.evaluateDraft(principal, draft.id(), withDish(original, MONDAY, "LUNCH.2", other));

        assertThat(edited.editedCells()).isEqualTo(1);
        assertThat(edited.checks())
                .filteredOn(c -> "VALUE_GATE".equals(c.code()))
                .singleElement()
                .satisfies(c -> assertThat(c.detail()).contains("đã sửa 1 ô"));
        assertThat(draftService.getDraft(principal, draft.id())).isEqualTo(draft);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_plan_weeks", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("Chấm thử lưới sai: món khác loại ô, thiếu ngày, thiếu ô, lặp món trong bữa ⇒ 400; nháp đã bỏ ⇒ 409")
    void evaluateRejectsInvalidGrid() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));
        MenuAiDraftGridRequest original = gridOf(draft);

        expectCode(
                () -> draftService.evaluateDraft(principal, draft.id(), withDish(original, MONDAY, "LUNCH.2", com)),
                ApiCode.INVALID_REQUEST_DATA);
        expectCode(
                () -> draftService.evaluateDraft(
                        principal,
                        draft.id(),
                        new MenuAiDraftGridRequest(original.days().subList(1, 5))),
                ApiCode.INVALID_REQUEST_DATA);
        List<MenuAiDraftGridRequest.Day> missingCell = new ArrayList<>(original.days());
        MenuAiDraftGridRequest.Day monday = missingCell.getFirst();
        missingCell.set(
                0, new MenuAiDraftGridRequest.Day(monday.date(), monday.items().subList(0, 2)));
        expectCode(
                () -> draftService.evaluateDraft(principal, draft.id(), new MenuAiDraftGridRequest(missingCell)),
                ApiCode.INVALID_REQUEST_DATA);
        expectCode(
                () -> draftService.evaluateDraft(principal, draft.id(), withDish(original, MONDAY, "LUNCH.9", thit)),
                ApiCode.INVALID_REQUEST_DATA);
        expectCode(() -> draftService.evaluateDraft(vp, draft.id(), original), ApiCode.FORBIDDEN);

        draftService.dismissDraft(principal, draft.id());
        expectCode(
                () -> draftService.evaluateDraft(principal, draft.id(), original), ApiCode.MENU_AI_DRAFT_NOT_PENDING);
    }

    @Test
    @DisplayName(
            "AI-02: dùng nháp kèm lưới đã sửa ⇒ tuần lưu đúng món đã sửa, khối khác giữ, lưu số ô đã sửa; gọi lại không ghi")
    void applyWithEditedGridSavesEditsAndCountsThem() {
        mealPlanService.saveMealPlan(principal, MONDAY, nurseryLunchOnMonday());
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));
        long other = otherMain(draft, MONDAY);
        MenuAiDraftGridRequest edited = withDish(gridOf(draft), MONDAY, "LUNCH.2", other);

        MealPlanResponse applied = draftService.applyDraft(principal, draft.id(), edited, null);

        assertThat(applied.status()).isEqualTo("DRAFT");
        assertThat(mainOf(applied, MONDAY)).isEqualTo(other);
        assertThat(applied.days().getFirst().items())
                .filteredOn(i -> i.block() == MenuBlock.NURSERY)
                .singleElement()
                .satisfies(i -> assertThat(i.dishId()).isEqualTo(com));
        MenuAiDraftResponse stored = draftService.getDraft(principal, draft.id());
        assertThat(stored.status()).isEqualTo(MenuAiDraftStatus.APPLIED);
        assertThat(stored.editedCells()).isEqualTo(1);
        // Lưới gợi ý gốc không đổi (đo giá trị AI)
        assertThat(stored.days()).isEqualTo(draft.days());
        Long itemsBefore = jdbc.queryForObject("SELECT count(*) FROM meal_plan_items", Long.class);

        draftService.applyDraft(principal, draft.id(), edited, null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_plan_items", Long.class))
                .isEqualTo(itemsBefore);
    }

    @Test
    @DisplayName(
            "NUT-09: lưới đã sửa còn lỗi chặn vẫn ghi vào tuần nháp nhưng không công bố được; thay một món không nhận lưới")
    void applyEditedGridWithBlockingStaysUnpublishable() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));
        MenuAiDraftGridRequest edited = gridOf(draft);
        String setKcal = "UPDATE age_group_meal_configs SET kcal_min = ?, kcal_max = ?"
                + " WHERE age_group_id = ? AND effective_from = '2026-09-01'";
        jdbc.update(setKcal, 1500, 1800, ag3);
        try {
            assertThat(draftService.evaluateDraft(principal, draft.id(), edited).feasible())
                    .isFalse();

            MealPlanResponse applied = draftService.applyDraft(principal, draft.id(), edited, null);

            assertThat(applied.days()).allSatisfy(d -> assertThat(d.items()).hasSize(3));
            expectCode(() -> mealPlanService.publishMealPlan(principal, MONDAY), ApiCode.MEAL_PLAN_NOT_PUBLISHABLE);
        } finally {
            jdbc.update(setKcal, 300, 600, ag3);
        }
    }

    private static MenuAiDraftGridRequest gridOf(MenuAiDraftResponse draft) {
        return new MenuAiDraftGridRequest(draft.days().stream()
                .filter(d -> !d.off())
                .map(d -> new MenuAiDraftGridRequest.Day(
                        d.date(),
                        d.items().stream()
                                .map(i -> new MenuAiDraftGridRequest.Item(i.slot(), i.dishId()))
                                .toList()))
                .toList());
    }

    private static MenuAiDraftGridRequest withDish(
            MenuAiDraftGridRequest grid, LocalDate date, String slot, long dishId) {
        return new MenuAiDraftGridRequest(grid.days().stream()
                .map(d -> !d.date().equals(date)
                        ? d
                        : new MenuAiDraftGridRequest.Day(
                                d.date(),
                                d.items().stream()
                                        .map(i -> i.slot().equals(slot)
                                                        || slot.equals("LUNCH.9")
                                                                && i.slot().equals("LUNCH.2")
                                                ? new MenuAiDraftGridRequest.Item(slot, dishId)
                                                : i)
                                        .toList()))
                .toList());
    }

    private List<MenuAiDraftCandidatesResponse.Dish> mainCandidates(MenuAiDraftCandidatesResponse candidates) {
        return candidates.slots().stream()
                .filter(s -> s.key().equals("LUNCH.2"))
                .findFirst()
                .orElseThrow()
                .dishes();
    }

    // Một món mặn ứng viên khác món gợi ý của ngày đó
    private long otherMain(MenuAiDraftResponse draft, LocalDate date) {
        long suggested = draft.days().stream()
                .filter(d -> date.equals(d.date()))
                .flatMap(d -> d.items().stream())
                .filter(i -> i.slot().equals("LUNCH.2"))
                .findFirst()
                .orElseThrow()
                .dishId();
        return mainCandidates(draftService.getCandidates(principal, draft.id())).stream()
                .map(MenuAiDraftCandidatesResponse.Dish::dishId)
                .filter(id -> id != suggested)
                .findFirst()
                .orElseThrow();
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

        expectCode(() -> draftService.applyDraft(principal, fix.id(), null, null), ApiCode.MENU_AI_DRAFT_STALE);
        assertThat(mainOf(mealPlanService.getMealPlan(principal, MONDAY), MONDAY))
                .isEqualTo(otherMain);
    }

    @Test
    @DisplayName("AC-13: món vừa dùng lấy theo ngày từ 4 tuần liền trước; nháp trả khoảng ngày đã đối chiếu")
    void historyWindowCoversFourPreviousWeeksByDate() {
        mealPlanService.saveMealPlan(principal, MONDAY, kindergartenMondayOnly());

        LocalDate fourWeeksLater = MONDAY.plusWeeks(4);
        MenuAiDraftResponse inside = draftService.createDraft(principal, weekly(fourWeeksLater));
        assertThat(inside.historyFrom()).isEqualTo(MONDAY);
        assertThat(inside.historyTo()).isEqualTo(fourWeeksLater.minusDays(1));
        assertThat(historyCheck(inside).status()).isEqualTo("INFO");
        assertThat(draftService.getDraft(principal, inside.id()).historyFrom()).isEqualTo(MONDAY);

        // Tuần thứ năm trở về trước nằm ngoài khoảng ⇒ không còn tính là vừa dùng
        MenuAiDraftResponse outside = draftService.createDraft(principal, weekly(MONDAY.plusWeeks(5)));
        assertThat(outside.historyFrom()).isEqualTo(MONDAY.plusWeeks(1));
        assertThat(historyCheck(outside).status()).isEqualTo("PASS");
    }

    private static MenuAiDraftResponse.Check historyCheck(MenuAiDraftResponse draft) {
        return draft.checks().stream()
                .filter(c -> "HISTORY".equals(c.code()))
                .findFirst()
                .orElseThrow();
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

    // Embedding giả: vector theo từ khóa trong văn bản (cá / đậu / thịt), còn lại một vector chung
    private void fakeEmbedding() {
        given(embeddingClient.isAvailable()).willReturn(true);
        given(embeddingClient.modelName()).willReturn("fake-embed");
        given(embeddingClient.budget()).willReturn(Duration.ofSeconds(5));
        given(embeddingClient.embed(any(), any())).willAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(MenuAiDraftIntegrationTest::fakeVector).toList();
        });
    }

    private static float[] fakeVector(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("cá")) {
            return new float[] {1f, 0f, 0f};
        }
        if (lower.contains("đậu")) {
            return new float[] {0f, 1f, 0f};
        }
        if (lower.contains("thịt")) {
            return new float[] {0f, 0f, 1f};
        }
        return new float[] {0.2f, 0.2f, 0.2f};
    }

    private static MenuAiDraftRequest weeklyWishing(String wishes) {
        return new MenuAiDraftRequest(
                MenuAiDraftKind.MENU_WEEKLY,
                MenuBlock.KINDERGARTEN,
                MONDAY,
                "Mùa đông",
                wishes,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private List<Long> mainCandidates(MenuAiDraftResponse draft) {
        return draftService.getCandidates(principal, draft.id()).slots().stream()
                .filter(s -> s.dishTypes().contains(DishType.MAIN))
                .findFirst()
                .orElseThrow()
                .dishes()
                .stream()
                .map(MenuAiDraftCandidatesResponse.Dish::dishId)
                .toList();
    }

    @Test
    @DisplayName(
            "AC-17: embedding bật + mong muốn ⇒ món gần nghĩa đứng đầu ứng viên của ô; vector lưu lười, lần sau dùng lại")
    void wishesReorderCandidatesByEmbedding() {
        fakeEmbedding();
        long tofu = jdbc.queryForObject("SELECT id FROM dishes WHERE code = 'AI-DAUSOT'", Long.class);
        long fish = jdbc.queryForObject("SELECT id FROM dishes WHERE code = 'AI-CAHAP'", Long.class);

        MenuAiDraftResponse draft = draftService.createDraft(principal, weeklyWishing("ăn đậu hũ"));

        // Không vector: thịt rim (hợp mùa theo bộ mẫu) trước, rồi theo id; có vector: đậu sốt lên đầu
        assertThat(mainCandidates(draft)).startsWith(tofu).contains(thit, fish);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dish_embeddings WHERE model = 'fake-embed'", Long.class))
                .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM dishes WHERE status = 'ACTIVE'", Long.class));
        verify(embeddingClient, times(2)).embed(any(), any());

        // Cùng mong muốn lần hai: câu truy vấn trong cache, vector món đã lưu ⇒ không gọi embedding nữa
        MenuAiDraftResponse again = draftService.createDraft(principal, weeklyWishing("ăn đậu hũ"));
        assertThat(mainCandidates(again)).startsWith(tofu);
        verify(embeddingClient, times(2)).embed(any(), any());
    }

    @Test
    @DisplayName("AC-17: embedding lỗi/hết thời gian ⇒ thứ tự ứng viên như không có vector, vẫn trả nháp")
    void embeddingFailureKeepsOrderAndDraft() {
        given(embeddingClient.isAvailable()).willReturn(false);
        List<Long> baseline = mainCandidates(draftService.createDraft(principal, weeklyWishing("ăn cá hấp")));

        given(embeddingClient.isAvailable()).willReturn(true);
        given(embeddingClient.modelName()).willReturn("fake-embed");
        given(embeddingClient.budget()).willReturn(Duration.ofSeconds(5));
        given(embeddingClient.embed(any(), any()))
                .willThrow(new AiClientException(AiErrorReason.TIMEOUT, "hết ngân sách", null));

        MenuAiDraftResponse draft = draftService.createDraft(principal, weeklyWishing("ăn cá hấp kho"));

        assertThat(draft.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(draft.days()).hasSize(5);
        assertThat(mainCandidates(draft)).isEqualTo(baseline);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dish_embeddings", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("AC-17, AI-03: embedding tắt ⇒ không gọi embedding, không ghi dish_embeddings")
    void embeddingDisabledDoesNothing() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weeklyWishing("ăn đậu phụ"));

        assertThat(draft.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        verify(embeddingClient, never()).embed(any(), any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dish_embeddings", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("AC-17: thay một món không kèm mong muốn ⇒ embed món cũ và danh mục, nháp vẫn thay bằng món khác")
    void dayFixEmbedsOldDishAndStillReplacesIt() {
        fakeEmbedding();
        mealPlanService.saveMealPlan(principal, MONDAY, kindergartenMondayOnly());

        MenuAiDraftResponse fix = draftService.createDraft(principal, dayFix(MONDAY, thit));

        assertThat(fix.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dish_embeddings WHERE dish_id = ?", Long.class, thit))
                .isEqualTo(1L);
        assertThat(fix.days().getFirst().items().getFirst().dishId()).isNotEqualTo(thit);
    }

    private static MenuAiDraftRequest daily() {
        return new MenuAiDraftRequest(
                MenuAiDraftKind.MENU_DAILY,
                MenuBlock.KINDERGARTEN,
                null,
                "Mùa đông",
                null,
                null,
                null,
                null,
                null,
                null,
                null);
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
                fromDish,
                null,
                null,
                null);
    }

    private static MenuAiDraftRequest weekly(LocalDate weekStart) {
        return weekly(MenuBlock.KINDERGARTEN, weekStart, null, null, null);
    }

    private static MenuAiDraftRequest weekly(
            MenuBlock block,
            LocalDate weekStart,
            MenuAiStrategy strategy,
            List<Long> unavailable,
            List<Long> required) {
        return new MenuAiDraftRequest(
                MenuAiDraftKind.MENU_WEEKLY,
                block,
                weekStart,
                "Mùa đông",
                "nhiều rau",
                null,
                null,
                null,
                strategy,
                unavailable,
                required);
    }

    private static List<Long> dishesOf(MenuAiDraftResponse draft) {
        return draft.days().stream()
                .flatMap(d -> d.items().stream())
                .map(MenuAiDraftResponse.Item::dishId)
                .toList();
    }

    @Test
    @DisplayName(
            "AC-14: chỉnh từ mẫu không ràng buộc ⇒ lưới giữ đúng thực đơn mẫu, không ô nào đổi; khối không mẫu ⇒ 400")
    void templateAdjustKeepsTemplateCells() {
        long sua = jdbc.queryForObject("SELECT id FROM dishes WHERE code = 'AI-SUACHUA'", Long.class);
        // Bộ mẫu seed chỉ có thứ Hai của tuần 1; tuần thử rơi vào tuần xoay vòng khác ⇒ chưa có mẫu để chỉnh
        expectCode(
                () -> draftService.createDraft(
                        principal, weekly(MenuBlock.KINDERGARTEN, MONDAY, MenuAiStrategy.TEMPLATE_ADJUST, null, null)),
                ApiCode.INVALID_REQUEST_DATA);
        expectCode(
                () -> draftService.createDraft(
                        principal, weekly(MenuBlock.NURSERY, MONDAY, MenuAiStrategy.TEMPLATE_ADJUST, null, null)),
                ApiCode.INVALID_REQUEST_DATA);

        withFullTemplate(sua, () -> {
            MenuAiDraftResponse draft = draftService.createDraft(
                    principal, weekly(MenuBlock.KINDERGARTEN, MONDAY, MenuAiStrategy.TEMPLATE_ADJUST, null, null));

            assertThat(draft.strategy()).isEqualTo(MenuAiStrategy.TEMPLATE_ADJUST);
            assertThat(draft.changedCells()).isEmpty();
            assertThat(draft.days())
                    .hasSize(5)
                    .allSatisfy(d -> assertThat(d.items())
                            .extracting(MenuAiDraftResponse.Item::dishId)
                            .containsExactly(com, thit, sua));
        });
    }

    /** Nạp thực đơn mẫu cơm + thịt + sữa cho cả 5 ngày của tuần xoay vòng chứa {@code MONDAY}, chạy xong thì gỡ. */
    private void withFullTemplate(long sua, Runnable body) {
        long set = jdbc.queryForObject("SELECT id FROM menu_template_sets WHERE name = 'Mẫu (giả)'", Long.class);
        int weekNo = (int) Math.floorMod((MONDAY.toEpochDay() - 4) / 7, 4) + 1;
        try {
            for (int weekday = 1; weekday <= 5; weekday++) {
                String sql = "INSERT INTO menu_template_items (set_id, week_no, weekday, session, dish_id, sort_order)"
                        + " VALUES (?, ?, ?, ?, ?, ?)";
                jdbc.update(sql, set, weekNo, weekday, "LUNCH", com, 0);
                jdbc.update(sql, set, weekNo, weekday, "LUNCH", thit, 1);
                jdbc.update(sql, set, weekNo, weekday, "AFTERNOON", sua, 0);
            }
            body.run();
        } finally {
            jdbc.update("DELETE FROM menu_template_items WHERE set_id = ? AND week_no = ?", set, weekNo);
        }
    }

    @Test
    @DisplayName("AC-15: thực phẩm đang thiếu ⇒ không ô nào dùng món chứa nó; chỉnh từ mẫu báo đúng các ô đã đổi")
    void unavailableFoodRemovesItsDishesAndReportsChangedCells() {
        long sua = jdbc.queryForObject("SELECT id FROM dishes WHERE code = 'AI-SUACHUA'", Long.class);
        withFullTemplate(sua, this::checkUnavailableFood);
    }

    private void checkUnavailableFood() {
        MenuAiDraftResponse free =
                draftService.createDraft(principal, weekly(MenuBlock.KINDERGARTEN, MONDAY, null, List.of(pork), null));
        assertThat(dishesOf(free)).isNotEmpty().doesNotContain(thit);
        assertThat(free.strategy()).isEqualTo(MenuAiStrategy.FREE);
        assertThat(free.changedCells()).isEmpty();

        MenuAiDraftResponse adjusted = draftService.createDraft(
                principal, weekly(MenuBlock.KINDERGARTEN, MONDAY, MenuAiStrategy.TEMPLATE_ADJUST, List.of(pork), null));
        assertThat(dishesOf(adjusted)).doesNotContain(thit);
        // Chỉ ô món mặn của mẫu (thịt) phải đổi; cơm và sữa giữ nguyên
        assertThat(adjusted.changedCells()).hasSize(5).allSatisfy(c -> {
            assertThat(c.slot()).isEqualTo("LUNCH.2");
            assertThat(c.fromDishId()).isEqualTo(thit);
            assertThat(c.fromDishName()).isEqualTo("Thịt rim (giả)");
            assertThat(c.toDishId()).isNotEqualTo(thit);
        });
        assertThat(draftService.getDraft(principal, adjusted.id())).isEqualTo(adjusted);
        // Danh sách món để sửa tay cũng không còn món chứa thực phẩm thiếu
        assertThat(mainCandidates(draftService.getCandidates(principal, adjusted.id())))
                .extracting(MenuAiDraftCandidatesResponse.Dish::dishId)
                .doesNotContain(thit);
    }

    @Test
    @DisplayName("AC-16: món bắt buộc có ít nhất một lần trong tuần; món không dùng được hoặc sai loại gợi ý ⇒ 400")
    void requiredDishAppearsInWeek() {
        long dau = jdbc.queryForObject("SELECT id FROM dishes WHERE code = 'AI-DAUSOT'", Long.class);
        long sua = jdbc.queryForObject("SELECT id FROM dishes WHERE code = 'AI-SUACHUA'", Long.class);

        withFullTemplate(sua, () -> {
            MenuAiDraftResponse draft = draftService.createDraft(
                    principal,
                    weekly(MenuBlock.KINDERGARTEN, MONDAY, MenuAiStrategy.TEMPLATE_ADJUST, null, List.of(dau)));

            assertThat(dishesOf(draft)).contains(dau);
            assertThat(draft.changedCells()).singleElement().satisfies(c -> {
                assertThat(c.fromDishId()).isEqualTo(thit);
                assertThat(c.toDishId()).isEqualTo(dau);
            });
        });
        assertThat(dishesOf(draftService.createDraft(
                        principal, weekly(MenuBlock.KINDERGARTEN, MONDAY, null, null, List.of(dau)))))
                .contains(dau);
        expectCode(
                () -> draftService.createDraft(
                        principal, weekly(MenuBlock.KINDERGARTEN, MONDAY, null, null, List.of(999_999L))),
                ApiCode.INVALID_REQUEST_DATA);
        // Món bắt buộc chứa chính thực phẩm đang thiếu ⇒ không áp được
        expectCode(
                () -> draftService.createDraft(
                        principal, weekly(MenuBlock.KINDERGARTEN, MONDAY, null, List.of(pork), List.of(thit))),
                ApiCode.INVALID_REQUEST_DATA);
        expectCode(
                () -> draftService.createDraft(
                        principal,
                        new MenuAiDraftRequest(
                                MenuAiDraftKind.MENU_DAILY,
                                MenuBlock.KINDERGARTEN,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                MenuAiStrategy.TEMPLATE_ADJUST,
                                null,
                                null)),
                ApiCode.INVALID_REQUEST_DATA);
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

    // Tuần đã công bố: thay món bằng gợi ý AI đi qua sửa ngoại lệ có sẵn (P-39)
    private MealPlanResponse publishedKindergartenWeek() {
        MenuAiDraftResponse draft = draftService.createDraft(principal, weekly(MONDAY));
        MealPlanResponse applied = draftService.applyDraft(principal, draft.id(), null, null);
        BalancedWeek.balance(dayBalanceService, principal, MONDAY);
        mealPlanService.publishMealPlan(principal, MONDAY);
        return applied;
    }

    @Test
    @DisplayName(
            "AC-24: tuần đã công bố, ngày chưa chốt ⇒ nháp thay món dùng qua sửa ngoại lệ: lưu lịch sử, tuần vẫn công bố")
    void dayFixDraftAmendsPublishedWeek() {
        MealPlanResponse before = publishedKindergartenWeek();
        long oldMain = mainOf(before, MONDAY);
        clock.set(MONDAY.minusDays(1), "07:00");

        MenuAiDraftResponse fix = draftService.createDraft(principal, dayFix(MONDAY, oldMain));
        long newMain = fix.days().getFirst().items().getFirst().dishId();
        MealPlanResponse amended =
                draftService.applyDraft(principal, fix.id(), null, "Nhà cung cấp báo hết nguyên liệu");

        assertThat(newMain).isNotEqualTo(oldMain);
        assertThat(amended.status()).isEqualTo("PUBLISHED");
        assertThat(mainOf(amended, MONDAY)).isEqualTo(newMain);
        assertThat(amended.revisionCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM meal_plan_day_revisions WHERE reason = ?",
                        Long.class,
                        "Nhà cung cấp báo hết nguyên liệu"))
                .isEqualTo(1L);
        assertThat(draftService.getDraft(principal, fix.id()).status()).isEqualTo(MenuAiDraftStatus.APPLIED);
        // Các ngày khác giữ nguyên
        assertThat(mainOf(amended, MONDAY.plusDays(1))).isEqualTo(mainOf(before, MONDAY.plusDays(1)));
    }

    @Test
    @DisplayName("AC-24, AC-25: ngày đã qua giờ chốt ⇒ không tạo nháp thay món (409 MEAL_PLAN_DAY_LOCKED)")
    void dayFixOnLockedDayIsRejected() {
        long oldMain = mainOf(publishedKindergartenWeek(), MONDAY);
        clock.set(MONDAY, "10:00");

        expectCode(() -> draftService.createDraft(principal, dayFix(MONDAY, oldMain)), ApiCode.MEAL_PLAN_DAY_LOCKED);
    }

    @Test
    @DisplayName("AC-24: dùng nháp thay món trên tuần đã công bố thiếu lý do (<10 ký tự) ⇒ 400, tuần không đổi")
    void amendingDraftNeedsReason() {
        MealPlanResponse before = publishedKindergartenWeek();
        long oldMain = mainOf(before, MONDAY);
        clock.set(MONDAY.minusDays(1), "07:00");
        MenuAiDraftResponse fix = draftService.createDraft(principal, dayFix(MONDAY, oldMain));

        expectCode(() -> draftService.applyDraft(principal, fix.id(), null, "ngắn"), ApiCode.MEAL_PLAN_REASON_REQUIRED);
        expectCode(() -> draftService.applyDraft(principal, fix.id(), null, null), ApiCode.MEAL_PLAN_REASON_REQUIRED);

        assertThat(mainOf(mealPlanService.getMealPlan(principal, MONDAY), MONDAY))
                .isEqualTo(oldMain);
        assertThat(draftService.getDraft(principal, fix.id()).status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("AC-24: nháp tuần hoặc nháp có lưới sửa trên tuần đã công bố vẫn bị chặn (409 MEAL_PLAN_PUBLISHED)")
    void onlyDayFixDraftsAmendPublishedWeek() {
        MenuAiDraftResponse weekDraft = draftService.createDraft(principal, weekly(MONDAY));
        draftService.applyDraft(principal, weekDraft.id(), null, null);
        MenuAiDraftResponse second = draftService.createDraft(principal, weekly(MONDAY));
        BalancedWeek.balance(dayBalanceService, principal, MONDAY);
        mealPlanService.publishMealPlan(principal, MONDAY);
        clock.set(MONDAY.minusDays(1), "07:00");

        expectCode(
                () -> draftService.applyDraft(principal, second.id(), null, "Lý do đủ dài để hợp lệ"),
                ApiCode.MEAL_PLAN_PUBLISHED);
    }
}
