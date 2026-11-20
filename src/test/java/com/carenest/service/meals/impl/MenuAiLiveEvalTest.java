package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.config.MenuAiProperties;
import com.carenest.integration.ai.AiGateway;
import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.PromptTemplateLoader;
import com.carenest.integration.ai.ProviderChainAiGateway;
import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.FoodInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.Norm;
import com.carenest.service.meals.impl.MealPlanEvaluator.Portion;
import com.carenest.service.meals.impl.MealPlanEvaluator.Range;
import com.carenest.utils.enums.AiDataClass;
import com.carenest.utils.enums.AiTask;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftSource;
import com.carenest.utils.enums.MenuAiDraftStatus;
import com.carenest.utils.enums.MenuBlock;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Eval chạy tay gợi ý thực đơn tuần với provider thật (AC-10, AC-18), không chạy trong CI: bật bằng {@code
 * AI_LIVE_EVAL=true} cùng biến {@code AI_GEMINI_*}, {@code AI_GROQ_*} như {@code .env}. Dữ liệu giả ở {@code
 * ai-eval/menu-cases.json}; mỗi case chạy {@code AI_LIVE_EVAL_RUNS} lần (mặc định 3) qua đúng engine thật. Ghi thư mục
 * {@code target/ai-eval/menu-eval-<thời điểm>/}: {@code records.json} (chỉ số từng lần chạy), {@code lines.csv} (món –
 * thực phẩm – gam – giá trị trên 100 g – phần đóng góp, để tester tính lại bằng bảng tính), {@code days.csv} (tổng ngày
 * hệ thống tính so với tổng cộng lại từ {@code lines.csv} và định mức). Chép vào {@code docs/quality/ai-eval/<ngày>/}.
 * Chặn cứng: 0 lần phương án nhãn AI còn lỗi chặn, 0 ô dùng món chứa thực phẩm thiếu, 0 ngày tổng kcal hệ thống lệch
 * tổng cộng tay; tỉ lệ AI thắng phương án quy tắc chỉ ghi lại (mốc bật demo ≥ 60% case hợp lệ).
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "AI_LIVE_EVAL", matches = "true")
class MenuAiLiveEvalTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    private static final long GROUP_ID = 3L;
    private static final AgeGroupInfo GROUP = new AgeGroupInfo(
            GROUP_ID, "Mẫu giáo bé (giả)", MenuBlock.KINDERGARTEN, List.of(MealSession.LUNCH, MealSession.AFTERNOON));
    private static final List<MenuSlot> SLOTS = List.of(
            new MenuSlot(MealSession.LUNCH, 0, Set.of(DishType.CARB)),
            new MenuSlot(MealSession.LUNCH, 1, Set.of(DishType.MAIN)),
            new MenuSlot(MealSession.LUNCH, 2, Set.of(DishType.SOUP)),
            new MenuSlot(MealSession.LUNCH, 3, Set.of(DishType.VEGETABLE)),
            new MenuSlot(MealSession.AFTERNOON, 0, Set.of(DishType.SNACK)));

    @Test
    void recordWeeklyMenuEval() throws IOException {
        JsonNode root = MAPPER.readTree(resource());
        int runs = Integer.parseInt(env("AI_LIVE_EVAL_RUNS", "3"));
        List<Map<String, Object>> records = new ArrayList<>();
        List<String> lines = new ArrayList<>(List.of(LINE_HEADER));
        List<String> days = new ArrayList<>(List.of(DAY_HEADER));
        for (JsonNode evalCase : root.get("cases")) {
            boolean badKeys = evalCase.path("badKeys").asBoolean(false);
            MenuSuggestionEngine engine = new MenuSuggestionEngine(
                    gateway(badKeys),
                    new PromptTemplateLoader(),
                    MAPPER,
                    MenuAiProperties.defaults(),
                    Clock.systemUTC());
            MenuContext ctx = context(root, evalCase);
            for (int run = 1; run <= runs; run++) {
                pauseForRateLimit();
                long started = System.nanoTime();
                MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);
                String name = evalCase.get("name").asString();
                Map<String, Object> record = record(name, run, outcome, started);
                record.putAll(evidence(name, run, outcome, ctx, ids(evalCase.path("unavailableFoods")), lines, days));
                records.add(record);
            }
        }
        Path out = writeReport(records, lines, days);
        long leaks = count(records, "blockingLeak");
        long unavailableLeaks = count(records, "unavailableLeak");
        long kcalMismatches = records.stream()
                .mapToLong(r -> ((Number) r.getOrDefault("kcalMismatchDays", 0)).longValue())
                .sum();
        List<Map<String, Object>> valid = records.stream()
                .filter(r -> !"provider-loi".equals(r.get("case")))
                .filter(r -> !"NO_FEASIBLE".equals(r.get("status")))
                .filter(r -> !Boolean.TRUE.equals(r.get("unmet")))
                .toList();
        long aiWins = valid.stream().filter(r -> "AI".equals(r.get("source"))).count();
        log.info(
                "MENU EVAL | records={} | valid={} | aiWins={} ({}%) | blockingLeaks={} | unavailableLeaks={}"
                        + " | kcalMismatchDays={} | report={}",
                records.size(),
                valid.size(),
                aiWins,
                valid.isEmpty() ? 0 : aiWins * 100 / valid.size(),
                leaks,
                unavailableLeaks,
                kcalMismatches,
                out);
        assertThat(leaks).as("phương án nhãn AI không được còn lỗi chặn").isZero();
        assertThat(unavailableLeaks)
                .as("lưới không được có món chứa thực phẩm thiếu")
                .isZero();
        assertThat(kcalMismatches)
                .as("tổng kcal hệ thống phải khớp tổng cộng tay từ lines.csv")
                .isZero();
    }

    private static final String LINE_HEADER = "case,run,source,date,session,slot,dish_id,dish,food_id,origin,grams,"
            + "kcal_100g,protein_100g,lipid_100g,glucid_100g,kcal,animal_protein,plant_protein,animal_lipid,"
            + "plant_lipid,glucid";
    private static final String DAY_HEADER = "case,run,source,date,status,kcal_system,kcal_recomputed,kcal_match,"
            + "kcal_min,kcal_max,kcal_in_range,protein,lipid,glucid,animal_protein,plant_protein,animal_lipid,"
            + "plant_lipid,cost,meal_price";
    private static final BigDecimal KCAL_TOLERANCE = new BigDecimal("0.1");

    /**
     * Bằng chứng cho tester (AC-18): cộng tay từng thực phẩm theo gam × giá trị/100 g, không qua evaluator, rồi so với
     * tổng ngày hệ thống tính; đếm món lặp trong tuần, lặp so lịch sử, ngày ngoài khoảng kcal, ô dùng thực phẩm thiếu.
     */
    private static Map<String, Object> evidence(
            String name,
            int run,
            MenuSuggestionEngine.Outcome outcome,
            MenuContext ctx,
            Set<Long> unavailableFoods,
            List<String> lines,
            List<String> days) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        MenuGrid grid = outcome.grid();
        if (grid == null) {
            return metrics;
        }
        String source = outcome.source() == null ? "" : outcome.source().name();
        Map<Long, Integer> uses = new HashMap<>();
        // NUT-20 cảnh báo lặp theo món mặn; cơm/canh lặp là bình thường nên đếm riêng
        Map<Long, Integer> mainUses = new HashMap<>();
        int historyRepeats = 0;
        boolean unavailableLeak = false;
        Map<LocalDate, BigDecimal> recomputed = new LinkedHashMap<>();
        Set<LocalDate> incomplete = new HashSet<>();
        for (LocalDate date : ctx.dates()) {
            if (ctx.offDays().contains(date)) {
                continue;
            }
            BigDecimal dayKcal = BigDecimal.ZERO;
            for (MenuSlot slot : ctx.slots()) {
                Long dishId = grid.dish(date, slot.key());
                if (dishId == null) {
                    continue;
                }
                uses.merge(dishId, 1, Integer::sum);
                if (ctx.dishes().get(dishId).type() == DishType.MAIN) {
                    mainUses.merge(dishId, 1, Integer::sum);
                }
                if (ctx.recentDishIds().contains(dishId)) {
                    historyRepeats++;
                }
                DishInfo dish = ctx.dishes().get(dishId);
                for (Portion p : dish.byAgeGroup().getOrDefault(GROUP_ID, List.of())) {
                    FoodInfo food = p.food();
                    unavailableLeak |= unavailableFoods.contains(food.id());
                    BigDecimal ratio = p.grams().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                    BigDecimal kcal = food.kcal() == null ? null : food.kcal().multiply(ratio);
                    boolean animal = food.origin() == FoodOrigin.ANIMAL;
                    BigDecimal protein = food.protein().multiply(ratio);
                    BigDecimal lipid = food.lipid().multiply(ratio);
                    if (kcal == null) {
                        incomplete.add(date);
                    } else {
                        dayKcal = dayKcal.add(kcal);
                    }
                    lines.add(csv(
                            name,
                            run,
                            source,
                            date,
                            slot.session(),
                            slot.key(),
                            dishId,
                            dish.name(),
                            food.id(),
                            food.origin(),
                            p.grams(),
                            food.kcal(),
                            food.protein(),
                            food.lipid(),
                            food.glucid(),
                            round2(kcal),
                            animal ? round2(protein) : BigDecimal.ZERO,
                            animal ? BigDecimal.ZERO : round2(protein),
                            animal ? round2(lipid) : BigDecimal.ZERO,
                            animal ? BigDecimal.ZERO : round2(lipid),
                            round2(food.glucid().multiply(ratio))));
                }
            }
            recomputed.put(date, dayKcal);
        }
        int outOfRange = 0;
        int mismatches = 0;
        for (var evaluation : grid.evaluate(ctx).evaluations()) {
            LocalDate date = evaluation.date();
            var totals = evaluation.totals();
            Range kcalRange = ctx.normOf().apply(evaluation.ageGroupId(), date).kcal();
            boolean complete = !incomplete.contains(date);
            BigDecimal mine = recomputed.get(date);
            boolean match = !complete
                    || (totals.kcal() != null
                            && totals.kcal().subtract(mine).abs().compareTo(KCAL_TOLERANCE) <= 0);
            boolean inRange = complete
                    && totals.kcal() != null
                    && totals.kcal().compareTo(kcalRange.min()) >= 0
                    && totals.kcal().compareTo(kcalRange.max()) <= 0;
            if (!match) {
                mismatches++;
            }
            if (!inRange) {
                outOfRange++;
            }
            days.add(csv(
                    name,
                    run,
                    source,
                    date,
                    evaluation.status(),
                    totals.kcal(),
                    complete ? round2(mine) : null,
                    complete ? match : null,
                    kcalRange.min(),
                    kcalRange.max(),
                    inRange,
                    totals.protein(),
                    totals.lipid(),
                    totals.glucid(),
                    totals.animalProtein(),
                    totals.plantProtein(),
                    totals.animalLipid(),
                    totals.plantLipid(),
                    evaluation.cost(),
                    evaluation.mealPrice()));
        }
        metrics.put(
                "repeatsWithinWeek", uses.values().stream().mapToInt(n -> n - 1).sum());
        metrics.put(
                "mainRepeatsWithinWeek",
                mainUses.values().stream().mapToInt(n -> n - 1).sum());
        metrics.put("repeatsVsHistory", historyRepeats);
        metrics.put("daysOutOfKcalRange", outOfRange);
        metrics.put("kcalMismatchDays", mismatches);
        metrics.put("unavailableLeak", unavailableLeak);
        return metrics;
    }

    private static BigDecimal round2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String csv(Object... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            Object v = values[i];
            String text = v == null ? "" : v instanceof BigDecimal d ? d.toPlainString() : v.toString();
            row.append(text.contains(",") || text.contains("\"") ? "\"" + text.replace("\"", "\"\"") + "\"" : text);
        }
        return row.toString();
    }

    private static long count(List<Map<String, Object>> records, String flag) {
        return records.stream().filter(r -> Boolean.TRUE.equals(r.get(flag))).count();
    }

    private static Map<String, Object> record(
            String name, int run, MenuSuggestionEngine.Outcome outcome, long startedNanos) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("case", name);
        record.put("run", run);
        record.put("status", outcome.status().name());
        record.put("source", outcome.source() == null ? null : outcome.source().name());
        record.put(
                "fallbackReason",
                outcome.fallbackReason() == null
                        ? null
                        : outcome.fallbackReason().name());
        record.put("aiScore", outcome.aiScore());
        record.put("baselineScore", outcome.baselineScore());
        record.put("provider", outcome.call().provider());
        record.put("model", outcome.call().model());
        record.put("repairRounds", outcome.call().repairRounds());
        record.put("modelLatencyMs", outcome.call().latencyMs());
        record.put("totalMs", (System.nanoTime() - startedNanos) / 1_000_000);
        record.put(
                "blockingLeak",
                outcome.source() == MenuAiDraftSource.AI
                        && outcome.status() == MenuAiDraftStatus.PENDING_REVIEW
                        && outcome.issues().stream().anyMatch(i -> MealPlanEvaluator.BLOCKING.equals(i.severity())));
        record.put(
                "unmet",
                outcome.status() == MenuAiDraftStatus.PENDING_REVIEW
                        && outcome.issues().stream().anyMatch(i -> MealPlanEvaluator.BLOCKING.equals(i.severity())));
        record.put(
                "checks",
                outcome.checks().stream().map(c -> c.code() + "=" + c.status()).toList());
        log.info(
                "EVAL | {} | run {} | {} | source={} | fallback={} | ai={} | base={}",
                name,
                run,
                outcome.status(),
                outcome.source(),
                outcome.fallbackReason(),
                outcome.aiScore(),
                outcome.baselineScore());
        return record;
    }

    private static MenuContext context(JsonNode root, JsonNode evalCase) {
        Set<Long> missingKcal = ids(evalCase.path("missingKcalFoods"));
        Map<Long, FoodInfo> foods = new HashMap<>();
        for (JsonNode f : root.get("foods")) {
            long id = f.get("id").asLong();
            List<String> allergens = new ArrayList<>();
            f.path("allergens").forEach(a -> allergens.add(a.asString()));
            foods.put(
                    id,
                    new FoodInfo(
                            id,
                            missingKcal.contains(id) ? null : f.get("kcal").decimalValue(),
                            f.get("protein").decimalValue(),
                            f.get("lipid").decimalValue(),
                            f.get("glucid").decimalValue(),
                            FoodOrigin.valueOf(f.get("origin").asString()),
                            f.get("price").decimalValue(),
                            BigDecimal.valueOf(1000),
                            BigDecimal.ZERO,
                            allergens));
        }
        Set<Long> keep = ids(evalCase.path("keepDishes"));
        // Như MenuAiContextLoader: món chứa thực phẩm thiếu bị loại khỏi danh mục trước khi dựng ứng viên (NUT-11)
        Set<Long> unavailable = ids(evalCase.path("unavailableFoods"));
        Set<Long> recent = ids(evalCase.path("recentDishes"));
        int copies = evalCase.path("duplicateCatalog").asInt(1);
        Map<Long, DishInfo> dishes = new HashMap<>();
        for (int copy = 0; copy < copies; copy++) {
            for (JsonNode d : root.get("dishes")) {
                long id = d.get("id").asLong();
                if (!keep.isEmpty() && !keep.contains(id)) {
                    continue;
                }
                if (unavailable.contains(d.get("food").asLong())) {
                    continue;
                }
                long copyId = id + copy * 1000L;
                dishes.put(
                        copyId,
                        new DishInfo(
                                copyId,
                                d.get("name").asString() + (copy == 0 ? "" : " #" + copy),
                                DishType.valueOf(d.get("type").asString()),
                                Map.of(
                                        GROUP_ID,
                                        List.of(new Portion(
                                                foods.get(d.get("food").asLong()),
                                                BigDecimal.valueOf(
                                                        d.get("grams").asInt()))))));
            }
        }
        JsonNode n = root.get("norm");
        BigDecimal price = evalCase.has("mealPrice")
                ? evalCase.get("mealPrice").decimalValue()
                : n.get("mealPrice").decimalValue();
        Norm norm = new Norm(
                price,
                new Range(n.get("kcalMin").decimalValue(), n.get("kcalMax").decimalValue()),
                range(n.get("proteinPct")),
                range(n.get("lipidPct")),
                range(n.get("glucidPct")),
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Map.of(),
                null);
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            dates.add(MONDAY.plusDays(i));
        }
        Map<String, List<Long>> candidates = MenuCandidateBuilder.build(
                dishes.values(),
                List.of(GROUP),
                SLOTS,
                recent,
                MenuContext.Signals.NONE,
                MenuAiProperties.defaults().candidateLimit());
        return new MenuContext(
                MenuBlock.KINDERGARTEN,
                dates,
                Set.of(),
                List.of(GROUP),
                dishes,
                (groupId, date) -> norm,
                SLOTS,
                candidates,
                recent,
                MenuContext.Signals.NONE,
                Map.of(),
                "Mùa đông",
                evalCase.path("wishes").isString() ? evalCase.get("wishes").asString() : null);
    }

    private static AiGateway gateway(boolean badKeys) {
        List<AiProperties.Provider> providers = List.of(
                provider(
                        "gemini",
                        "AI_GEMINI",
                        "https://generativelanguage.googleapis.com/v1beta/openai",
                        true,
                        badKeys),
                provider("groq", "AI_GROQ", "https://api.groq.com/openai/v1", false, badKeys));
        AiProperties.TaskConfig weekly = new AiProperties.TaskConfig(
                List.of("gemini", "groq"),
                AiDataClass.PUBLIC_CATALOG,
                Duration.parse("PT" + env("AI_MENU_WEEKLY_DEADLINE", "30s").toUpperCase()),
                2);
        return new ProviderChainAiGateway(new AiProperties(true, providers, Map.of(AiTask.MENU_WEEKLY, weekly)));
    }

    private static AiProperties.Provider provider(
            String id, String prefix, String defaultUrl, boolean mayTrain, boolean badKey) {
        return new AiProperties.Provider(
                id,
                env(prefix + "_BASE_URL", defaultUrl),
                env(prefix + "_MODEL", ""),
                badKey ? "invalid-key-for-eval" : env(prefix + "_API_KEY", ""),
                mayTrain,
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                env(prefix + "_REASONING_EFFORT", ""));
    }

    private static Range range(JsonNode pair) {
        return new Range(pair.get(0).decimalValue(), pair.get(1).decimalValue());
    }

    private static Set<Long> ids(JsonNode array) {
        Set<Long> ids = new HashSet<>();
        array.forEach(v -> ids.add(v.asLong()));
        return ids;
    }

    private static InputStream resource() {
        return MenuAiLiveEvalTest.class.getResourceAsStream("/ai-eval/menu-cases.json");
    }

    private static Path writeReport(List<Map<String, Object>> records, List<String> lines, List<String> days)
            throws IOException {
        Path dir = Path.of("target", "ai-eval")
                .resolve("menu-eval-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("records.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(records));
        // BOM để Excel đọc đúng tiếng Việt khi mở trực tiếp
        Files.writeString(dir.resolve("lines.csv"), "﻿" + String.join("\n", lines) + "\n");
        Files.writeString(dir.resolve("days.csv"), "﻿" + String.join("\n", days) + "\n");
        return dir;
    }

    // Hạn mức free tính theo phút: giãn các lần gọi (mặc định 4s, đặt AI_LIVE_EVAL_PAUSE_MS để đổi)
    private static void pauseForRateLimit() {
        try {
            Thread.sleep(Long.parseLong(env("AI_LIVE_EVAL_PAUSE_MS", "4000")));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
