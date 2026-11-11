package com.carenest.service.meals.impl;

import static com.carenest.service.meals.impl.MenuFixtures.CA;
import static com.carenest.service.meals.impl.MenuFixtures.CHUOI;
import static com.carenest.service.meals.impl.MenuFixtures.COM;
import static com.carenest.service.meals.impl.MenuFixtures.DAU;
import static com.carenest.service.meals.impl.MenuFixtures.DISHES;
import static com.carenest.service.meals.impl.MenuFixtures.MONDAY;
import static com.carenest.service.meals.impl.MenuFixtures.SUA;
import static com.carenest.service.meals.impl.MenuFixtures.THIT;
import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.config.MenuAiProperties;
import com.carenest.dto.meals.MealPlanResponse.Issue;
import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.AiGateway;
import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.AiRawResult;
import com.carenest.integration.ai.AiRequest;
import com.carenest.integration.ai.PromptTemplateLoader;
import com.carenest.utils.enums.AiDataClass;
import com.carenest.utils.enums.AiErrorReason;
import com.carenest.utils.enums.AiTask;
import com.carenest.utils.enums.MenuAiDraftSource;
import com.carenest.utils.enums.MenuAiDraftStatus;
import com.carenest.utils.enums.MenuAiFallbackReason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Cổng giá trị (bước 3–8): quyết định nguồn nháp với AiGateway giả (AC-3, AC-4, AC-7). */
class MenuSuggestionEngineTest {

    private final FakeGateway gateway = new FakeGateway();
    private final MenuSuggestionEngine engine = new MenuSuggestionEngine(
            gateway,
            new PromptTemplateLoader(),
            JsonMapper.builder().build(),
            MenuAiProperties.defaults(),
            Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("AC-7: AI tắt ⇒ phương án theo quy tắc qua cổng cứng, AI_DISABLED, không gọi model")
    void disabledFallsBackToRuleBased() {
        gateway.available = false;

        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(MenuFixtures.ctx());

        assertThat(outcome.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(outcome.source()).isEqualTo(MenuAiDraftSource.RULE_BASED);
        assertThat(outcome.fallbackReason()).isEqualTo(MenuAiFallbackReason.AI_DISABLED);
        assertThat(outcome.issues()).noneMatch(MenuSuggestionEngineTest::blocking);
        assertThat(outcome.aiScore()).isNull();
        assertThat(gateway.requests).isEmpty();
    }

    @Test
    @DisplayName(
            "AC-3: AI qua cổng cứng và điểm ≥ phương án quy tắc ⇒ source=AI, lưới đúng output, prompt không có id thật")
    void validBetterAiWins() {
        MenuContext ctx = MenuFixtures.ctx();
        long[] mains = {THIT, CA, DAU, THIT, CA};
        long[] snacks = {SUA, SUA, SUA, CHUOI, SUA};
        gateway.respond(r -> week(ctx, mains, snacks, "Đổi món mặn, thêm trái cây."));

        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);

        assertThat(outcome.source()).isEqualTo(MenuAiDraftSource.AI);
        assertThat(outcome.fallbackReason()).isNull();
        assertThat(outcome.aiScore()).isGreaterThanOrEqualTo(outcome.baselineScore());
        assertThat(outcome.grid().dish(MONDAY.plusDays(3), MenuFixtures.AFTERNOON.key()))
                .isEqualTo(CHUOI);
        assertThat(outcome.grid().reasons()).containsEntry(MONDAY, "Đổi món mặn, thêm trái cây.");
        assertThat(outcome.call().provider()).isEqualTo("fake");
        assertThat(outcome.call().promptVersion()).isEqualTo(MenuSuggestionEngine.WEEKLY_PROMPT);
        // AI-04: chỉ danh mục công khai dưới bí danh, không id món thật
        assertThat(gateway.requests.getFirst().userPrompt()).contains("D1|").doesNotContain("|" + THIT + "|");
    }

    @Test
    @DisplayName("AC-3: AI hợp lệ nhưng điểm thấp hơn (lặp một bộ món cả tuần) ⇒ RULE_BASED, AI_NOT_BETTER")
    void validWorseAiLoses() {
        MenuContext ctx = MenuFixtures.ctx();
        gateway.respond(
                r -> week(ctx, new long[] {THIT, THIT, THIT, THIT, THIT}, new long[] {SUA, SUA, SUA, SUA, SUA}, ""));

        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);

        assertThat(outcome.source()).isEqualTo(MenuAiDraftSource.RULE_BASED);
        assertThat(outcome.fallbackReason()).isEqualTo(MenuAiFallbackReason.AI_NOT_BETTER);
        assertThat(outcome.aiScore()).isLessThan(outcome.baselineScore());
    }

    @Test
    @DisplayName("AC-2: output sai cấu trúc ở mọi vòng ⇒ RULE_BASED, AI_INVALID; gửi lại lỗi gọn cho model")
    void invalidOutputEveryRoundFallsBack() {
        gateway.respond(
                r -> "{\"days\":[{\"date\":\"2026-11-16\",\"items\":[{\"slot\":\"LUNCH.1\",\"dish\":\"D99\"}]}]}");

        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(MenuFixtures.ctx());

        assertThat(outcome.source()).isEqualTo(MenuAiDraftSource.RULE_BASED);
        assertThat(outcome.fallbackReason()).isEqualTo(MenuAiFallbackReason.AI_INVALID);
        assertThat(gateway.requests).hasSize(3);
        assertThat(gateway.requests.get(1).userPrompt()).contains("Lần trả lời trước không dùng được");
        assertThat(outcome.call().repairRounds()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-7: provider 429 ⇒ AI_UNAVAILABLE; timeout ⇒ AI_TIMEOUT; vẫn có phương án theo quy tắc")
    void providerErrorsFallBack() {
        gateway.fail(AiErrorReason.RATE_LIMITED);
        assertThat(engine.suggestWeek(MenuFixtures.ctx()).fallbackReason())
                .isEqualTo(MenuAiFallbackReason.AI_UNAVAILABLE);

        gateway.fail(AiErrorReason.TIMEOUT);
        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(MenuFixtures.ctx());
        assertThat(outcome.fallbackReason()).isEqualTo(MenuAiFallbackReason.AI_TIMEOUT);
        assertThat(outcome.source()).isEqualTo(MenuAiDraftSource.RULE_BASED);
    }

    @Test
    @DisplayName("Bước 7: ngày AI lệch định mức được sửa (code rồi model) ⇒ phương án chọn không còn lỗi chặn")
    void blockingAiDayIsRepaired() {
        MenuContext ctx = MenuFixtures.ctx();
        // Thứ Hai cá + chuối: béo 6,8% < 15% ⇒ lỗi chặn
        gateway.respond(
                r -> week(ctx, new long[] {CA, THIT, DAU, CA, THIT}, new long[] {CHUOI, SUA, SUA, SUA, SUA}, ""));

        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);

        assertThat(outcome.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(outcome.issues()).noneMatch(MenuSuggestionEngineTest::blocking);
        assertThat(outcome.grid().evaluate(ctx).publishable()).isTrue();
    }

    @Test
    @DisplayName("AC-4: ô không có món ứng viên ⇒ NO_FEASIBLE kèm nguyên nhân theo ô, không gọi model")
    void noCandidatesIsNoFeasible() {
        Map<Long, MealPlanEvaluator.DishInfo> noSnack = new HashMap<>(DISHES);
        noSnack.remove(SUA);
        noSnack.remove(CHUOI);
        noSnack.remove(MenuFixtures.BANH);
        MenuContext ctx = MenuFixtures.ctx(noSnack, MenuFixtures.norm("300", "600"), Set.of(), Set.of(), Map.of());

        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);

        assertThat(outcome.status()).isEqualTo(MenuAiDraftStatus.NO_FEASIBLE);
        assertThat(outcome.source()).isNull();
        assertThat(outcome.issues()).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("NO_CANDIDATES");
            assertThat(i.message()).contains("bữa chiều");
        });
        assertThat(gateway.requests).isEmpty();
    }

    @Test
    @DisplayName("AC-4: cả AI và phương án quy tắc không đạt định mức ⇒ vẫn trả lưới theo quy tắc kèm lỗi chặn")
    void bothFailingKeepsRuleBasedGridWithBlockingIssues() {
        MenuContext ctx = MenuFixtures.ctx(DISHES, MenuFixtures.norm("1500", "1800"), Set.of(), Set.of(), Map.of());
        gateway.respond(r -> week(ctx, new long[] {THIT, CA, DAU, CA, THIT}, new long[] {SUA, SUA, SUA, SUA, SUA}, ""));

        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);

        assertThat(outcome.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(outcome.source()).isEqualTo(MenuAiDraftSource.RULE_BASED);
        assertThat(outcome.fallbackReason()).isEqualTo(MenuAiFallbackReason.AI_INVALID);
        assertThat(outcome.issues()).anyMatch(MenuSuggestionEngineTest::blocking);
        assertThat(outcome.grid().cells()).hasSize(5);
        assertThat(outcome.checks())
                .anySatisfy(c -> assertThat(c.code() + "=" + c.status()).isEqualTo("NORM_BALANCE=FAIL"));
        // Món nhiều năng lượng nhất mỗi ô: cơm 210 + thịt 100 + bánh 700 = 1010 < 1500 ⇒ nói rõ lỗi do dữ liệu
        assertThat(outcome.checks())
                .filteredOn(c -> MenuCriteriaReport.KCAL_REACH.equals(c.code()))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.status()).isEqualTo(MenuCriteriaReport.FAIL);
                    assertThat(c.detail()).contains("1010 kcal").contains("tối thiểu 1500 kcal");
                });
    }

    @Test
    @DisplayName("Giới hạn năng lượng của món ứng viên: chỉ báo khi không cách chọn món nào tới được khoảng định mức")
    void kcalReachOnlyReportsProvableDataLimits() {
        assertThat(MenuCriteriaReport.kcalReach(MenuFixtures.ctx())).isNull();
        // Món ít năng lượng nhất mỗi ô: cơm 210 + cá 60 + chuối 90 = 360 > 200
        MenuContext tooRich = MenuFixtures.ctx(DISHES, MenuFixtures.norm("100", "200"), Set.of(), Set.of(), Map.of());
        assertThat(MenuCriteriaReport.kcalReach(tooRich).detail())
                .contains("360 kcal")
                .contains("tối đa 200 kcal");
        // Ô không có món ứng viên ⇒ không đủ căn cứ để kết luận
        MenuContext noSnack = withCandidates(
                tooRich,
                Map.of(
                        MenuFixtures.LUNCH_CARB.key(), List.of(COM),
                        MenuFixtures.LUNCH_MAIN.key(), List.of(CA),
                        MenuFixtures.AFTERNOON.key(), List.of()));
        assertThat(MenuCriteriaReport.kcalReach(noSnack)).isNull();
    }

    @Test
    @DisplayName("Ngày nghỉ không được điền món, không yêu cầu model lập")
    void offDaysStayEmpty() {
        LocalDate wednesday = MONDAY.plusDays(2);
        gateway.available = false;
        MenuContext ctx =
                MenuFixtures.ctx(DISHES, MenuFixtures.norm("300", "600"), Set.of(wednesday), Set.of(), Map.of());

        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);

        assertThat(outcome.grid().cells()).doesNotContainKey(wednesday).hasSize(4);
    }

    @Test
    @DisplayName("Kho khô hết: món bị cảnh báo DRY_STOCK_OUT, trừ điểm; prompt gắn nhãn hợp mùa và kho khô đang hết")
    void stockAndSeasonSignalsReachScoreAndPrompt() {
        MenuContext.Signals signals = new MenuContext.Signals(Set.of(DAU), "Mùa đông", Set.of(CA), Set.of(THIT));
        MenuContext ctx =
                MenuFixtures.ctx(DISHES, MenuFixtures.norm("300", "600"), Set.of(), Set.of(), signals, Map.of());
        gateway.respond(
                r -> week(ctx, new long[] {THIT, CA, DAU, THIT, CA}, new long[] {SUA, SUA, SUA, CHUOI, SUA}, ""));

        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);

        String prompt = gateway.requests.getFirst().userPrompt();
        assertThat(prompt).contains("hợp mùa").contains("trường đã dùng").contains("kho khô đang hết");
        // Phương án được chọn (AI hay quy tắc) dùng thịt hết kho thì phải có cảnh báo và bảng kiểm báo WARN
        boolean usesPork = outcome.grid().cells().values().stream().anyMatch(day -> day.containsValue(THIT));
        assertThat(outcome.issues().stream().anyMatch(i -> MenuCriteriaReport.DRY_STOCK_OUT.equals(i.code())))
                .isEqualTo(usesPork);
        assertThat(outcome.checks())
                .extracting(c -> c.code() + "=" + c.status())
                .contains(
                        "NORM_BALANCE=PASS",
                        "CATALOG=PASS",
                        "DRY_STOCK_OUT=" + (usesPork ? MenuCriteriaReport.WARN : MenuCriteriaReport.PASS));
        assertThat(outcome.checks())
                .filteredOn(c -> "SEASON".equals(c.code()))
                .singleElement()
                .satisfies(c -> assertThat(c.detail()).contains("Mùa đông"));
    }

    @Test
    @DisplayName("Món dùng thực phẩm kho khô đang hết nằm trong phương án ⇒ cảnh báo, bảng kiểm báo WARN")
    void outOfStockDishInChosenPlanIsWarned() {
        gateway.available = false;
        MenuContext.Signals signals = new MenuContext.Signals(Set.of(), null, Set.of(), Set.of(COM));
        MenuContext ctx =
                MenuFixtures.ctx(DISHES, MenuFixtures.norm("300", "600"), Set.of(), Set.of(), signals, Map.of());

        MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);

        // Cơm là món CARB duy nhất nên vẫn phải dùng, nhưng được cảnh báo để nhập thêm trước ngày đầu tuần
        assertThat(outcome.issues()).anySatisfy(i -> {
            assertThat(i.code()).isEqualTo(MenuCriteriaReport.DRY_STOCK_OUT);
            assertThat(i.dishId()).isEqualTo(COM);
            assertThat(i.date()).isEqualTo(MONDAY);
        });
        assertThat(outcome.checks()).anySatisfy(c -> {
            assertThat(c.code()).isEqualTo(MenuCriteriaReport.DRY_STOCK_OUT);
            assertThat(c.status()).isEqualTo(MenuCriteriaReport.WARN);
        });
    }

    @Test
    @DisplayName("AC-9 mẫu ngày: AI tắt ⇒ một ngày đủ ô qua kiểm, không gọi model")
    void dailyFallsBackToRuleBased() {
        gateway.available = false;
        MenuContext week = MenuFixtures.ctx();
        MenuContext oneDay = new MenuContext(
                week.block(),
                List.of(MONDAY),
                Set.of(),
                week.groups(),
                week.dishes(),
                week.normOf(),
                week.slots(),
                week.candidates(),
                Set.of(),
                MenuContext.Signals.NONE,
                Map.of(),
                null,
                null);

        MenuSuggestionEngine.Outcome outcome = engine.suggestDaily(oneDay);

        assertThat(outcome.source()).isEqualTo(MenuAiDraftSource.RULE_BASED);
        assertThat(outcome.grid().cells()).containsOnlyKeys(MONDAY);
        assertThat(outcome.grid().cells().get(MONDAY)).hasSize(3);
        assertThat(outcome.issues()).noneMatch(MenuSuggestionEngineTest::blocking);
    }

    @Test
    @DisplayName("AC-9 thay món: AI chọn món cùng loại qua kiểm ⇒ chỉ đổi đúng ô; bí danh ngoài ô ⇒ AI_INVALID")
    void dayFixChangesOnlyTargetCell() {
        MenuContext base = MenuFixtures.ctx();
        MenuGrid current = MenuFixtures.sameEveryDay(base, COM, THIT, SUA);
        MenuSlot main = MenuFixtures.LUNCH_MAIN;
        MenuContext ctx = withCandidates(base, Map.of(main.key(), List.of(CA, DAU)));
        MenuAiPromptBuilder.Aliases aliases = MenuAiPromptBuilder.aliases(ctx);
        gateway.respond(r -> "{\"dish\":\"" + aliases.toAlias().get(DAU) + "\",\"reason\":\"Đổi sang đạm thực vật.\"}");

        MenuSuggestionEngine.Outcome outcome = engine.suggestDayFix(ctx, current, MONDAY, main);

        assertThat(outcome.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(outcome.grid().dish(MONDAY, main.key())).isIn(CA, DAU);
        assertThat(outcome.grid().dish(MONDAY.plusDays(1), main.key())).isEqualTo(THIT);
        assertThat(outcome.call().promptVersion()).isEqualTo(MenuSuggestionEngine.DAY_FIX_PROMPT);
        assertThat(gateway.requests.getFirst().userPrompt()).contains("cần thay");

        gateway.requests.clear();
        gateway.respond(r -> "{\"dish\":\"D99\"}");
        assertThat(engine.suggestDayFix(ctx, current, MONDAY, main).fallbackReason())
                .isEqualTo(MenuAiFallbackReason.AI_INVALID);
    }

    @Test
    @DisplayName("Thay món ở tuần mới lập một ngày: ngày trống khác không làm phương án thay bị chặn")
    void dayFixIgnoresBlockingOfOtherDays() {
        gateway.available = false;
        MenuContext base = MenuFixtures.ctx();
        MenuGrid mondayOnly = new MenuGrid(
                Map.of(
                        MONDAY,
                        Map.of(
                                MenuFixtures.LUNCH_CARB.key(), COM,
                                MenuFixtures.LUNCH_MAIN.key(), THIT,
                                MenuFixtures.AFTERNOON.key(), SUA)),
                Map.of());
        MenuContext ctx = withCandidates(base, Map.of(MenuFixtures.LUNCH_MAIN.key(), List.of(CA, DAU)));

        MenuSuggestionEngine.Outcome outcome = engine.suggestDayFix(ctx, mondayOnly, MONDAY, MenuFixtures.LUNCH_MAIN);

        assertThat(outcome.status()).isEqualTo(MenuAiDraftStatus.PENDING_REVIEW);
        assertThat(outcome.issues()).noneMatch(MenuSuggestionEngineTest::blocking);
    }

    private static MenuContext withCandidates(MenuContext ctx, Map<String, List<Long>> candidates) {
        return new MenuContext(
                ctx.block(),
                ctx.dates(),
                ctx.offDays(),
                ctx.groups(),
                ctx.dishes(),
                ctx.normOf(),
                ctx.slots(),
                candidates,
                ctx.recentDishIds(),
                ctx.signals(),
                Map.of(),
                null,
                null);
    }

    private static boolean blocking(Issue issue) {
        return MealPlanEvaluator.BLOCKING.equals(issue.severity());
    }

    private static String week(MenuContext ctx, long[] mains, long[] snacks, String reason) {
        MenuAiPromptBuilder.Aliases aliases = MenuAiPromptBuilder.aliases(ctx);
        List<String> days = new ArrayList<>();
        List<LocalDate> dates = ctx.servedDates();
        for (int i = 0; i < dates.size(); i++) {
            days.add("{\"date\":\"" + dates.get(i) + "\",\"items\":[{\"slot\":\"LUNCH.1\",\"dish\":\""
                    + aliases.toAlias().get(COM) + "\"},{\"slot\":\"LUNCH.2\",\"dish\":\""
                    + aliases.toAlias().get(mains[i]) + "\"},{\"slot\":\"AFTERNOON.1\",\"dish\":\""
                    + aliases.toAlias().get(snacks[i]) + "\"}],\"reason\":\"" + reason + "\"}");
        }
        return "{\"days\":[" + String.join(",", days) + "]}";
    }

    /** Gateway giả: trả nội dung theo hàm hoặc ném lỗi provider; ghi lại request để kiểm prompt. */
    private static final class FakeGateway implements AiGateway {
        boolean available = true;
        final List<AiRequest> requests = new ArrayList<>();
        private Function<AiRequest, String> responder = r -> "{}";
        private final Deque<AiErrorReason> failures = new ArrayDeque<>();

        void respond(Function<AiRequest, String> responder) {
            this.responder = responder;
        }

        void fail(AiErrorReason reason) {
            failures.clear();
            failures.add(reason);
        }

        @Override
        public boolean isAvailable(AiTask task) {
            return available;
        }

        @Override
        public Optional<AiProperties.TaskConfig> taskConfig(AiTask task) {
            return Optional.of(new AiProperties.TaskConfig(
                    List.of("fake"), AiDataClass.PUBLIC_CATALOG, Duration.ofSeconds(30), 2));
        }

        @Override
        public AiRawResult complete(AiRequest request) {
            requests.add(request);
            if (!failures.isEmpty()) {
                throw new AiClientException(failures.poll(), "fake failure", null);
            }
            return new AiRawResult(responder.apply(request), "fake", "fake-model", request.promptVersion(), 10, 10, 5);
        }
    }
}
