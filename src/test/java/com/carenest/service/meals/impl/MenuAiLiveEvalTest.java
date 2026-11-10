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
 * Eval chạy tay gợi ý thực đơn tuần với provider thật (AC-10), không chạy trong CI: bật bằng {@code AI_LIVE_EVAL=true}
 * cùng biến {@code AI_GEMINI_*}, {@code AI_GROQ_*} như {@code .env}. Dữ liệu giả ở {@code ai-eval/menu-cases.json}; mỗi
 * case chạy {@code AI_LIVE_EVAL_RUNS} lần (mặc định 3) qua đúng engine thật. Ghi {@code target/ai-eval/menu-eval-*.json}
 * để chép vào {@code docs/quality/ai-eval/<ngày>/}. Chặn cứng: 0 lần phương án nhãn AI còn lỗi chặn; tỉ lệ AI thắng
 * phương án quy tắc chỉ ghi lại (mốc bật demo ≥ 60% case hợp lệ).
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
                records.add(record(evalCase.get("name").asString(), run, outcome, started));
            }
        }
        Path out = writeReport(records);
        long leaks = records.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("blockingLeak")))
                .count();
        List<Map<String, Object>> valid = records.stream()
                .filter(r -> !"provider-loi".equals(r.get("case")))
                .filter(r -> !"NO_FEASIBLE".equals(r.get("status")))
                .toList();
        long aiWins = valid.stream().filter(r -> "AI".equals(r.get("source"))).count();
        log.info(
                "MENU EVAL | records={} | valid={} | aiWins={} ({}%) | blockingLeaks={} | report={}",
                records.size(), valid.size(), aiWins, valid.isEmpty() ? 0 : aiWins * 100 / valid.size(), leaks, out);
        assertThat(leaks).as("phương án nhãn AI không được còn lỗi chặn").isZero();
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
        int copies = evalCase.path("duplicateCatalog").asInt(1);
        Map<Long, DishInfo> dishes = new HashMap<>();
        for (int copy = 0; copy < copies; copy++) {
            for (JsonNode d : root.get("dishes")) {
                long id = d.get("id").asLong();
                if (!keep.isEmpty() && !keep.contains(id)) {
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
                Set.of(),
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
                Set.of(),
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

    private static Path writeReport(List<Map<String, Object>> records) throws IOException {
        Path dir = Path.of("target", "ai-eval");
        Files.createDirectories(dir);
        Path out = dir.resolve(
                "menu-eval-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
        Files.writeString(out, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(records));
        return out;
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
