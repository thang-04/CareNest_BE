package com.carenest.service.meals.impl;

import com.carenest.config.MenuAiProperties;
import com.carenest.dto.meals.MealPlanResponse.Issue;
import com.carenest.dto.meals.MenuAiDraftResponse.Check;
import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.AiGateway;
import com.carenest.integration.ai.AiProperties;
import com.carenest.integration.ai.AiRawResult;
import com.carenest.integration.ai.AiRequest;
import com.carenest.integration.ai.PromptTemplateLoader;
import com.carenest.utils.enums.AiErrorReason;
import com.carenest.utils.enums.AiTask;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftSource;
import com.carenest.utils.enums.MenuAiDraftStatus;
import com.carenest.utils.enums.MenuAiFallbackReason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Cổng giá trị gợi ý thực đơn tuần (bước 3–8, AI-07, NUT-09): phương án theo quy tắc luôn có; phương án AI chỉ được
 * chọn khi qua cổng cứng (guard cấu trúc + không lỗi chặn của evaluator) và điểm không thấp hơn phương án theo quy tắc.
 * Tuần/mẫu ngày không phương án nào qua cổng cứng ⇒ vẫn trả phương án theo quy tắc kèm lỗi chặn để PHT sửa tay; công
 * bố vẫn bị chặn ở kế hoạch tuần (NUT-09). Không I/O DB; lỗi provider không làm hỏng nghiệp vụ (AI-03).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MenuSuggestionEngine {

    static final String WEEKLY_PROMPT = "menu/weekly_v1";
    static final String REPAIR_PROMPT = "menu/repair_v1";
    static final String DAILY_PROMPT = "menu/daily_v1";
    static final String DAY_FIX_PROMPT = "menu/dayfix_v1";
    // Không mở vòng gọi model mới khi còn ít hơn mức này tới deadline (một lần gọi hiếm khi nhanh hơn)
    private static final Duration MIN_CALL_BUDGET = Duration.ofSeconds(3);
    private static final Map<MealSession, String> SESSION_LABELS =
            Map.of(MealSession.MORNING, "sáng", MealSession.LUNCH, "trưa", MealSession.AFTERNOON, "chiều");

    private final AiGateway aiGateway;
    private final PromptTemplateLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final MenuAiProperties properties;
    private final Clock clock;

    /**
     * Kết quả cổng giá trị; {@code grid} rỗng và {@code source} null khi {@code NO_FEASIBLE}. {@code PENDING_REVIEW} mà
     * {@code issues} còn lỗi chặn = phương án theo quy tắc chưa đạt định mức, chỉ để làm điểm bắt đầu.
     */
    record Outcome(
            MenuAiDraftStatus status,
            MenuAiDraftSource source,
            MenuAiFallbackReason fallbackReason,
            MenuGrid grid,
            List<Issue> issues,
            Integer aiScore,
            Integer baselineScore,
            MenuScore.Breakdown breakdown,
            ModelCall call,
            List<Check> checks) {}

    /** Metadata lần gọi model (AI-01, AI-05): không có prompt hay output. */
    record ModelCall(String provider, String model, String promptVersion, int repairRounds, long latencyMs) {
        static final ModelCall NONE = new ModelCall(null, null, null, 0, 0);
    }

    private record Candidate(MenuGrid grid, MealPlanEvaluator.Result result, MenuScore.Breakdown score) {
        boolean passes() {
            return result.publishable();
        }
    }

    Outcome suggestWeek(MenuContext ctx) {
        return suggestPlan(ctx, AiTask.MENU_WEEKLY, WEEKLY_PROMPT);
    }

    /** Thực đơn mẫu một ngày (AI-07 tùy chọn): cùng các bước như tuần, lưới một ngày. */
    Outcome suggestDaily(MenuContext ctx) {
        return suggestPlan(ctx, AiTask.MENU_DAILY, DAILY_PROMPT);
    }

    private Outcome suggestPlan(MenuContext ctx, AiTask task, String firstPrompt) {
        List<Issue> causes = infeasibleCauses(ctx);
        if (!causes.isEmpty()) {
            return noFeasible(ctx, causes, null, null, null, ModelCall.NONE);
        }
        Candidate baseline = judge(MenuPlanRepairer.repair(RuleBasedMenuPlanner.plan(ctx), ctx), ctx);
        return decide(ctx, baseline, attemptAi(ctx, task, firstPrompt), true);
    }

    /**
     * Thay một món của tuần nháp (AI-07 tùy chọn): chỉ ô {@code slot} ngày {@code date} được đổi, ô khác giữ như tuần
     * hiện có. Phương án theo quy tắc = món cùng loại điểm cao nhất mà ngày đó không còn lỗi chặn.
     */
    Outcome suggestDayFix(MenuContext ctx, MenuGrid current, LocalDate date, MenuSlot slot) {
        List<Long> candidates = ctx.candidatesOf(slot);
        MenuContext dayView = ctx.onlyDay(date);
        if (candidates.isEmpty()) {
            return noFeasible(
                    dayView,
                    List.of(cause(MenuAiPromptBuilder.blockLabel(ctx.block()) + " · bữa "
                            + SESSION_LABELS.get(slot.session())
                            + ": chưa có món cùng loại đang dùng, đủ dữ liệu để thay")),
                    null,
                    null,
                    null,
                    ModelCall.NONE);
        }
        Candidate baseline = null;
        for (Long dishId : candidates) {
            Candidate trial = judgeDay(current.with(date, slot.key(), dishId), ctx, date);
            if (baseline == null
                    || (trial.passes() && !baseline.passes())
                    || (trial.passes() == baseline.passes()
                            && trial.score().total() > baseline.score().total())) {
                baseline = trial;
            }
        }
        return decide(dayView, baseline, attemptDayFix(ctx, current, date, slot), false);
    }

    /**
     * Tuần nháp có thể còn ngày chưa lập xong: cổng cứng của thay một món chỉ xét lỗi chặn của đúng ngày đó (và lỗi
     * chưa đặt định mức, áp cho mọi ngày); cảnh báo cả tuần vẫn giữ để chấm điểm.
     */
    private Candidate judgeDay(MenuGrid grid, MenuContext ctx, LocalDate date) {
        Candidate week = judge(grid, ctx);
        List<Issue> issues = week.result().issues().stream()
                .filter(i -> !MealPlanEvaluator.BLOCKING.equals(i.severity())
                        || i.date() == null
                        || date.equals(i.date())
                        || "NORM_NOT_SET".equals(i.code()))
                .toList();
        MealPlanEvaluator.Result result =
                new MealPlanEvaluator.Result(week.result().evaluations(), issues);
        return new Candidate(grid, result, MenuScore.score(grid, result, ctx, properties.weights()));
    }

    // Bước 8: AI chỉ được chọn khi qua cổng cứng và không thua phương án theo quy tắc. keepUnmet: không phương án nào
    // qua cổng cứng thì vẫn đưa phương án theo quy tắc (thay một món thì không: đổi sang món vẫn lỗi là vô ích)
    private static Outcome decide(MenuContext ctx, Candidate baseline, AiAttempt ai, boolean keepUnmet) {
        Candidate aiCandidate = ai.candidate();
        Integer aiScore = aiCandidate == null ? null : aiCandidate.score().total();
        int baseScore = baseline.score().total();
        if (aiCandidate != null && aiCandidate.passes() && (!baseline.passes() || aiScore >= baseScore)) {
            return chosen(ctx, aiCandidate, MenuAiDraftSource.AI, null, aiScore, baseScore, ai.call());
        }
        MenuAiFallbackReason reason =
                aiCandidate != null && aiCandidate.passes() ? MenuAiFallbackReason.AI_NOT_BETTER : ai.failure();
        if (!baseline.passes() && !keepUnmet) {
            return noFeasible(ctx, blocking(baseline.result()), reason, aiScore, baseScore, ai.call());
        }
        return chosen(ctx, baseline, MenuAiDraftSource.RULE_BASED, reason, aiScore, baseScore, ai.call());
    }

    private static Outcome chosen(
            MenuContext ctx,
            Candidate candidate,
            MenuAiDraftSource source,
            MenuAiFallbackReason reason,
            Integer aiScore,
            int baseScore,
            ModelCall call) {
        List<Issue> issues = candidate.result().issues();
        return new Outcome(
                MenuAiDraftStatus.PENDING_REVIEW,
                source,
                reason,
                candidate.grid(),
                issues,
                aiScore,
                baseScore,
                candidate.score(),
                call,
                MenuCriteriaReport.build(candidate.grid(), issues, ctx, source, reason, aiScore, baseScore));
    }

    /** Kết quả chấm một lưới bất kỳ (vd. lưới người duyệt đã sửa) bằng đúng cách chấm của cổng giá trị. */
    record Assessment(MealPlanEvaluator.Result result, MenuScore.Breakdown score) {}

    Assessment assess(MenuContext ctx, MenuGrid grid) {
        Candidate judged = judge(grid, ctx);
        return new Assessment(judged.result(), judged.score());
    }

    private record AiAttempt(Candidate candidate, MenuAiFallbackReason failure, ModelCall call) {}

    private AiAttempt attemptAi(MenuContext ctx, AiTask task, String firstPrompt) {
        AiProperties.TaskConfig config = aiGateway.taskConfig(task).orElse(null);
        if (config == null || !aiGateway.isAvailable(task)) {
            return new AiAttempt(null, MenuAiFallbackReason.AI_DISABLED, ModelCall.NONE);
        }
        Instant deadline = clock.instant().plus(config.deadline());
        MenuAiPromptBuilder.Aliases aliases = MenuAiPromptBuilder.aliases(ctx);
        Set<LocalDate> expected = new LinkedHashSet<>(ctx.servedDates());
        String promptName = firstPrompt;
        String user = MenuAiPromptBuilder.weeklyUserPrompt(ctx, aliases);
        CallLog calls = new CallLog(firstPrompt);
        Candidate current = null;
        MenuAiFallbackReason failure = MenuAiFallbackReason.AI_INVALID;
        // Vòng 0 = gọi lần đầu; mỗi vòng sửa sau đó gửi lại lỗi (cấu trúc hoặc lỗi chặn) cho model
        for (int round = 0; round <= config.maxRepairRounds(); round++) {
            if (round > 0 && remaining(deadline).compareTo(MIN_CALL_BUDGET) < 0) {
                break;
            }
            AiRawResult raw;
            try {
                raw = aiGateway.complete(
                        new AiRequest(task, promptName, promptLoader.load(promptName), user, deadline));
            } catch (AiClientException ex) {
                // Đã có output đọc được mà vẫn lỗi ⇒ lỗi thuộc output; chưa có ⇒ lỗi provider
                failure = current == null && calls.parsed == 0 ? mapFailure(ex.getReason()) : failure;
                break;
            }
            calls.record(raw);
            Set<LocalDate> wanted = current == null ? expected : blockingDates(current.result());
            MenuAiOutputParser.Parsed parsed =
                    MenuAiOutputParser.parse(raw.content(), ctx, aliases, wanted, objectMapper);
            if (!parsed.valid()) {
                log.info("[MenuSuggestionEngine]|suggestWeek|round={}|output=INVALID", round);
                if (current == null) {
                    user = MenuAiPromptBuilder.retryUserPrompt(ctx, aliases, parsed.error());
                }
                continue;
            }
            calls.parsed++;
            MenuGrid grid = current == null ? parsed.grid() : current.grid().merge(parsed.grid());
            current = judge(MenuPlanRepairer.repair(grid, ctx), ctx);
            if (current.passes()) {
                break;
            }
            Map<LocalDate, List<Issue>> byDay = blockingByDay(current.result());
            promptName = REPAIR_PROMPT;
            user = MenuAiPromptBuilder.repairUserPrompt(ctx, aliases, current.grid(), byDay);
        }
        if (current == null && remaining(deadline).isNegative() && calls.count > 0) {
            failure = MenuAiFallbackReason.AI_TIMEOUT;
        }
        return new AiAttempt(current, failure, calls.toModelCall());
    }

    private AiAttempt attemptDayFix(MenuContext ctx, MenuGrid current, LocalDate date, MenuSlot slot) {
        AiTask task = AiTask.MENU_DAY_FIX;
        AiProperties.TaskConfig config = aiGateway.taskConfig(task).orElse(null);
        if (config == null || !aiGateway.isAvailable(task)) {
            return new AiAttempt(null, MenuAiFallbackReason.AI_DISABLED, ModelCall.NONE);
        }
        Instant deadline = clock.instant().plus(config.deadline());
        MenuAiPromptBuilder.Aliases aliases = MenuAiPromptBuilder.aliases(ctx);
        String base = MenuAiPromptBuilder.dayFixUserPrompt(ctx, aliases, current, date, slot);
        String user = base;
        CallLog calls = new CallLog(DAY_FIX_PROMPT);
        Candidate best = null;
        MenuAiFallbackReason failure = MenuAiFallbackReason.AI_INVALID;
        for (int round = 0; round <= config.maxRepairRounds(); round++) {
            if (round > 0 && remaining(deadline).compareTo(MIN_CALL_BUDGET) < 0) {
                break;
            }
            AiRawResult raw;
            try {
                raw = aiGateway.complete(
                        new AiRequest(task, DAY_FIX_PROMPT, promptLoader.load(DAY_FIX_PROMPT), user, deadline));
            } catch (AiClientException ex) {
                failure = calls.parsed == 0 ? mapFailure(ex.getReason()) : failure;
                break;
            }
            calls.record(raw);
            MenuAiOutputParser.Choice choice =
                    MenuAiOutputParser.parseChoice(raw.content(), aliases, ctx.candidatesOf(slot), objectMapper);
            if (!choice.valid()) {
                user = base + "Lần trả lời trước không dùng được: " + choice.error() + ".\n";
                continue;
            }
            calls.parsed++;
            MenuGrid grid = current.with(date, slot.key(), choice.dishId());
            Candidate trial = judgeDay(
                    choice.reason().isEmpty() ? grid : new MenuGrid(grid.cells(), Map.of(date, choice.reason())),
                    ctx,
                    date);
            best = trial;
            if (trial.passes()) {
                break;
            }
            user = base + "Món vừa chọn làm ngày còn lỗi: "
                    + blocking(trial.result()).stream()
                            .filter(i -> date.equals(i.date()))
                            .map(i -> MenuAiPromptBuilder.clean(i.message(), 160))
                            .collect(Collectors.joining("; "))
                    + ". Chọn món khác.\n";
        }
        if (best == null && remaining(deadline).isNegative() && calls.count > 0) {
            failure = MenuAiFallbackReason.AI_TIMEOUT;
        }
        return new AiAttempt(best, failure, calls.toModelCall());
    }

    /** Gom metadata các lần gọi: provider/model của lần cuối, tổng latency. */
    private static final class CallLog {
        final String firstPrompt;
        int count;
        int parsed;
        long latencyMs;
        String provider;
        String model;

        CallLog(String firstPrompt) {
            this.firstPrompt = firstPrompt;
        }

        void record(AiRawResult raw) {
            count++;
            latencyMs += raw.latencyMs();
            provider = raw.providerId();
            model = raw.model();
        }

        ModelCall toModelCall() {
            return count == 0 ? ModelCall.NONE : new ModelCall(provider, model, firstPrompt, count - 1, latencyMs);
        }
    }

    // Evaluator + cảnh báo kho khô đang hết (NUT-11): cùng một cách chấm cho AI và phương án theo quy tắc
    private Candidate judge(MenuGrid grid, MenuContext ctx) {
        MealPlanEvaluator.Result evaluated = grid.evaluate(ctx);
        List<Issue> issues = new ArrayList<>(evaluated.issues());
        issues.addAll(MenuCriteriaReport.stockIssues(grid, ctx));
        MealPlanEvaluator.Result result = new MealPlanEvaluator.Result(evaluated.evaluations(), issues);
        return new Candidate(grid, result, MenuScore.score(grid, result, ctx, properties.weights()));
    }

    // Không dựng được phương án nào: khối không có nhóm tuổi, tuần nghỉ hết, hoặc ô không có món ứng viên
    private static List<Issue> infeasibleCauses(MenuContext ctx) {
        List<Issue> causes = new ArrayList<>();
        String block = MenuAiPromptBuilder.blockLabel(ctx.block());
        if (ctx.groups().isEmpty()) {
            causes.add(cause(block + ": không có nhóm tuổi nào được phục vụ bữa"));
            return causes;
        }
        if (ctx.servedDates().isEmpty()) {
            causes.add(cause(block + ": không có ngày phục vụ để lập"));
            return causes;
        }
        for (MenuSlot slot : ctx.slots()) {
            if (ctx.candidatesOf(slot).isEmpty()) {
                causes.add(cause(block + " · bữa " + SESSION_LABELS.get(slot.session()) + " ô " + (slot.index() + 1)
                        + " (" + slot.types().stream().map(Enum::name).sorted().collect(Collectors.joining("/"))
                        + "): chưa có món đang dùng đủ định lượng và dữ liệu dinh dưỡng cho mọi nhóm tuổi"));
            }
        }
        return causes;
    }

    private static Issue cause(String message) {
        return new Issue(MealPlanEvaluator.BLOCKING, "NO_CANDIDATES", null, null, null, null, message);
    }

    private static Outcome noFeasible(
            MenuContext ctx,
            List<Issue> causes,
            MenuAiFallbackReason reason,
            Integer aiScore,
            Integer baseScore,
            ModelCall call) {
        return new Outcome(
                MenuAiDraftStatus.NO_FEASIBLE,
                null,
                reason,
                MenuGrid.empty(),
                causes,
                aiScore,
                baseScore,
                null,
                call,
                MenuCriteriaReport.build(MenuGrid.empty(), causes, ctx, null, reason, aiScore, baseScore));
    }

    private static List<Issue> blocking(MealPlanEvaluator.Result result) {
        return result.issues().stream()
                .filter(i -> MealPlanEvaluator.BLOCKING.equals(i.severity()))
                .toList();
    }

    private static Map<LocalDate, List<Issue>> blockingByDay(MealPlanEvaluator.Result result) {
        Map<LocalDate, List<Issue>> byDay = new TreeMap<>();
        blocking(result).stream()
                .filter(i -> i.date() != null)
                .forEach(i ->
                        byDay.computeIfAbsent(i.date(), d -> new ArrayList<>()).add(i));
        return new LinkedHashMap<>(byDay);
    }

    private static Set<LocalDate> blockingDates(MealPlanEvaluator.Result result) {
        return new LinkedHashSet<>(blockingByDay(result).keySet());
    }

    private Duration remaining(Instant deadline) {
        return Duration.between(clock.instant(), deadline);
    }

    private static MenuAiFallbackReason mapFailure(AiErrorReason reason) {
        return switch (reason) {
            case TIMEOUT -> MenuAiFallbackReason.AI_TIMEOUT;
            case DISABLED -> MenuAiFallbackReason.AI_DISABLED;
            case OUTPUT_INVALID -> MenuAiFallbackReason.AI_INVALID;
            case RATE_LIMITED, PROVIDER_UNAVAILABLE -> MenuAiFallbackReason.AI_UNAVAILABLE;
        };
    }
}
