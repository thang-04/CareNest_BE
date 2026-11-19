package com.carenest.service.meals.impl;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.meals.MealPlanAmendRequest;
import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MenuAiDraftCandidatesResponse;
import com.carenest.dto.meals.MenuAiDraftEvaluationResponse;
import com.carenest.dto.meals.MenuAiDraftGridRequest;
import com.carenest.dto.meals.MenuAiDraftRequest;
import com.carenest.dto.meals.MenuAiDraftResponse;
import com.carenest.entity.meals.AgeGroupMealConfig;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.entity.meals.MenuAiDraft;
import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.repository.meals.MenuAiDraftRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.MealPlanService;
import com.carenest.service.meals.MenuAiDraftService;
import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DayPlan;
import com.carenest.service.meals.impl.MealPlanEvaluator.ItemPlan;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftKind;
import com.carenest.utils.enums.MenuAiDraftStatus;
import com.carenest.utils.enums.MenuAiStrategy;
import com.carenest.utils.enums.MenuBlock;
import com.carenest.utils.enums.Permission;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MenuAiDraftServiceImpl implements MenuAiDraftService {
    private static final String LOG_PREFIX = "[MenuAiDraftServiceImpl]";
    private static final int DAYS = MealPlanEvaluationSupport.SCHOOL_DAYS;

    private final MenuAiDraftRepository draftRepository;
    private final MealPlanWeekRepository weekRepository;
    private final DishRepository dishRepository;
    private final MealPlanEvaluationSupport evaluationSupport;
    private final MealPlanService mealPlanService;
    private final MenuAiContextLoader contextLoader;
    private final DishSimilarity dishSimilarity;
    private final MenuSuggestionEngine engine;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** {@code historyFrom}–{@code historyTo}: khoảng ngày đã đối chiếu để tránh lặp món (null ở nháp cũ). */
    record StoredRequest(
            String season,
            String wishes,
            LocalDate historyFrom,
            LocalDate historyTo,
            MenuAiStrategy strategy,
            List<Long> unavailableFoodIds,
            List<Long> requiredDishIds) {

        // Đọc lại ngữ cảnh của nháp đã có: cùng ràng buộc lúc tạo, phần không còn áp được thì bỏ qua
        MenuAiContextLoader.Constraints constraints() {
            return new MenuAiContextLoader.Constraints(
                    idSet(unavailableFoodIds),
                    idSet(requiredDishIds),
                    strategy == MenuAiStrategy.TEMPLATE_ADJUST,
                    false);
        }
    }

    record StoredItem(String slot, MealSession session, Long dishId) {}

    record StoredDay(LocalDate date, boolean off, String reason, List<StoredItem> items) {}

    /** {@code changedCells}: ô khác thực đơn mẫu khi chỉnh từ mẫu (null ở nháp cũ). */
    record StoredOutput(List<StoredDay> days, List<MenuPlanConstraints.Changed> changedCells) {}

    private static Set<Long> idSet(List<Long> ids) {
        return ids == null ? Set.of() : new TreeSet<>(ids);
    }

    record StoredScore(
            Integer ai,
            Integer baseline,
            MenuAiDraftResponse.Breakdown breakdown,
            List<MenuAiDraftResponse.Check> checks) {}

    /** Ngữ cảnh + kết quả cổng giá trị của một yêu cầu, sẵn sàng lưu. */
    private record Suggestion(MenuContext ctx, MenuSuggestionEngine.Outcome outcome, StoredOutput output) {}

    /**
     * Gọi model đồng bộ tới ~30s (AI-07, D3) nên không giữ transaction/kết nối DB khi chờ: đọc ngữ cảnh và lưu nháp mỗi
     * bước một transaction ngắn.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MenuAiDraftResponse createDraft(CurrentUser currentUser, MenuAiDraftRequest request) {
        String logPrefix = LOG_PREFIX + "|createDraft|userId=" + currentUser.userId() + "|kind=" + request.kind()
                + "|block=" + request.block() + "|weekStart=" + request.weekStart();
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            checkRequest(request);
            Suggestion suggestion = suggest(currentUser, request);
            MenuAiDraftResponse response = transactionTemplate.execute(status -> {
                MenuAiDraft draft = draftRepository.save(toDraft(currentUser, request, suggestion));
                return toResponse(draft);
            });
            log.info(
                    "{}|END|draftId={}|status={}|source={}|fallback={}",
                    logPrefix,
                    response.id(),
                    response.status(),
                    response.source(),
                    response.fallbackReason());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MenuAiDraftResponse getDraft(CurrentUser currentUser, Long id) {
        String logPrefix = LOG_PREFIX + "|getDraft|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            MenuAiDraftResponse response = toResponse(findDraft(id));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MenuAiDraftResponse dismissDraft(CurrentUser currentUser, Long id) {
        String logPrefix = LOG_PREFIX + "|dismissDraft|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            MenuAiDraft draft = findDraft(id);
            if (!draft.isPending()) {
                throw GlobalException.of(ApiCode.MENU_AI_DRAFT_NOT_PENDING);
            }
            draft.dismiss(currentUser.userId(), now());
            draftRepository.saveAndFlush(draft);
            MenuAiDraftResponse response = toResponse(draft);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MealPlanResponse applyDraft(CurrentUser currentUser, Long id, MenuAiDraftGridRequest edited, String reason) {
        String logPrefix = LOG_PREFIX + "|applyDraft|userId=" + currentUser.userId() + "|draftId=" + id + "|edited="
                + (edited != null);
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            MenuAiDraft draft = findDraft(id);
            // Mẫu ngày không ghi vào kế hoạch tuần (A2): Web điền form thực đơn mẫu rồi đánh dấu đã dùng
            if (draft.getKind() == MenuAiDraftKind.MENU_DAILY) {
                throw GlobalException.of(ApiCode.MENU_AI_DRAFT_KIND_INVALID);
            }
            LocalDate weekStart = draft.getWeekStart();
            // Gọi lại sau khi đã dùng: trả tuần hiện tại, không ghi lại (idempotent)
            if (draft.getStatus() == MenuAiDraftStatus.APPLIED) {
                MealPlanResponse current = mealPlanService.getMealPlan(currentUser, weekStart);
                log.info("{}|END|alreadyApplied", logPrefix);
                return current;
            }
            if (!draft.isPending()) {
                throw GlobalException.of(ApiCode.MENU_AI_DRAFT_NOT_PENDING);
            }
            MealPlanWeek week = weekRepository.findByWeekStart(weekStart).orElse(null);
            StoredOutput output = objectMapper.readValue(draft.getOutputJson(), StoredOutput.class);
            if (week != null && week.isPublished()) {
                // NUT-08: tuần đã công bố chỉ nhận nháp thay một món (không kèm lưới sửa), qua sửa ngoại lệ có sẵn
                // (P-39)
                if (draft.getKind() != MenuAiDraftKind.MENU_DAY_FIX || edited != null) {
                    throw GlobalException.of(ApiCode.MEAL_PLAN_PUBLISHED);
                }
                MealPlanResponse response = amendWithDraft(currentUser, draft, week, output, reason);
                draft.applyEdited(currentUser.userId(), now(), 0);
                draftRepository.saveAndFlush(draft);
                log.info("{}|END|amended", logPrefix);
                return response;
            }
            int editedCells = 0;
            MealPlanRequest request;
            if (edited != null) {
                // Người duyệt đã sửa món trên nháp (AI-02): ghi lưới đã sửa, lưới gốc giữ để đo giá trị AI
                MenuContext ctx = pendingWeeklyContext(currentUser, draft);
                MenuGrid grid = editedGrid(ctx, output, edited);
                editedCells = editedCells(output, grid);
                request = mergedWeek(weekStart, week, draft, gridOutput(ctx, grid), true);
            } else {
                request = draft.getKind() == MenuAiDraftKind.MENU_DAY_FIX
                        ? replacedDish(draft, week, output)
                        : mergedWeek(weekStart, week, draft, output, false);
            }
            MealPlanResponse response = mealPlanService.saveMealPlan(currentUser, weekStart, request);
            draft.applyEdited(currentUser.userId(), now(), editedCells);
            draftRepository.saveAndFlush(draft);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MenuAiDraftResponse markDailyUsed(CurrentUser currentUser, Long id) {
        String logPrefix = LOG_PREFIX + "|markDailyUsed|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            MenuAiDraft draft = findDraft(id);
            if (draft.getKind() != MenuAiDraftKind.MENU_DAILY) {
                throw GlobalException.of(ApiCode.MENU_AI_DRAFT_KIND_INVALID);
            }
            if (draft.isPending()) {
                draft.apply(currentUser.userId(), now());
                draftRepository.saveAndFlush(draft);
            } else if (draft.getStatus() != MenuAiDraftStatus.APPLIED) {
                throw GlobalException.of(ApiCode.MENU_AI_DRAFT_NOT_PENDING);
            }
            MenuAiDraftResponse response = toResponse(draft);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MenuAiDraftCandidatesResponse getCandidates(CurrentUser currentUser, Long id) {
        String logPrefix = LOG_PREFIX + "|getCandidates|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            MenuContext ctx = pendingWeeklyContext(currentUser, findDraft(id));
            MenuContext.Signals signals = ctx.signals();
            List<MenuAiDraftCandidatesResponse.Slot> slots = ctx.slots().stream()
                    .map(slot -> new MenuAiDraftCandidatesResponse.Slot(
                            slot.key(),
                            slot.session(),
                            slot.index(),
                            slot.types().stream().sorted().toList(),
                            ctx.candidatesOf(slot).stream()
                                    .map(dishId -> new MenuAiDraftCandidatesResponse.Dish(
                                            dishId,
                                            ctx.dishes().get(dishId).name(),
                                            ctx.dishes().get(dishId).type(),
                                            signals.seasonal().contains(dishId),
                                            signals.familiar().contains(dishId),
                                            signals.outOfStock().contains(dishId)))
                                    .toList()))
                    .toList();
            log.info("{}|END|slots={}", logPrefix, slots.size());
            return new MenuAiDraftCandidatesResponse(slots);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MenuAiDraftEvaluationResponse evaluateDraft(CurrentUser currentUser, Long id, MenuAiDraftGridRequest grid) {
        String logPrefix = LOG_PREFIX + "|evaluateDraft|userId=" + currentUser.userId() + "|draftId=" + id;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            MenuAiDraft draft = findDraft(id);
            MenuContext ctx = pendingWeeklyContext(currentUser, draft);
            StoredOutput output = objectMapper.readValue(draft.getOutputJson(), StoredOutput.class);
            MenuGrid edited = editedGrid(ctx, output, grid);
            int editedCells = editedCells(output, edited);
            // Cùng cách chấm với lúc tạo nháp (evaluator + cảnh báo kho khô + điểm): số liệu chỉ do code tính (NUT-05)
            MenuSuggestionEngine.Assessment assessment = engine.assess(ctx, edited);
            List<MealPlanResponse.Issue> issues = assessment.result().issues();
            StoredScore stored = objectMapper.readValue(draft.getScoreJson(), StoredScore.class);
            MenuScore.Breakdown b = assessment.score();
            MenuAiDraftEvaluationResponse response = new MenuAiDraftEvaluationResponse(
                    assessment.result().publishable(),
                    editedCells,
                    new MenuAiDraftEvaluationResponse.Score(
                            b.total(),
                            new MenuAiDraftResponse.Breakdown(b.norm(), b.cost(), b.variety(), b.warnings())),
                    assessment.result().evaluations(),
                    issues,
                    MenuCriteriaReport.withEdits(
                            MenuCriteriaReport.build(
                                    edited,
                                    issues,
                                    ctx,
                                    draft.getSource(),
                                    draft.getFallbackReason(),
                                    stored.ai(),
                                    stored.baseline()),
                            editedCells));
            log.info("{}|END|editedCells={}|feasible={}", logPrefix, editedCells, response.feasible());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Sửa món chỉ có ở gợi ý tuần đang chờ duyệt; ngữ cảnh đọc lại trên dữ liệu hiện tại (tuần đã công bố ⇒ 409)
    private MenuContext pendingWeeklyContext(CurrentUser user, MenuAiDraft draft) {
        if (draft.getKind() != MenuAiDraftKind.MENU_WEEKLY) {
            throw GlobalException.of(ApiCode.MENU_AI_DRAFT_KIND_INVALID);
        }
        if (!draft.isPending()) {
            throw GlobalException.of(ApiCode.MENU_AI_DRAFT_NOT_PENDING);
        }
        StoredRequest stored = objectMapper.readValue(draft.getRequestJson(), StoredRequest.class);
        return contextLoader.weekly(
                user,
                draft.getMenuBlock(),
                draft.getWeekStart(),
                stored.season(),
                stored.wishes(),
                stored.constraints(),
                // Đọc lại ngữ cảnh khi sửa/dùng nháp: không gọi mạng trong transaction, dùng vector và cache đã có
                dishSimilarity.byWishesCached(stored.wishes()));
    }

    private static Map<LocalDate, Map<String, Long>> cellsOf(StoredOutput output) {
        Map<LocalDate, Map<String, Long>> cells = new TreeMap<>();
        for (StoredDay day : output.days()) {
            Map<String, Long> bySlot = new LinkedHashMap<>();
            day.items().forEach(item -> bySlot.put(item.slot(), item.dishId()));
            cells.put(day.date(), bySlot);
        }
        return cells;
    }

    /**
     * Lưới người duyệt gửi lên (NUT-05, NUT-09): đủ đúng các ngày phục vụ, mỗi ô của khung đúng một món, món thuộc ứng
     * viên của ô (hoặc là món gợi ý gốc của chính ô đó), không lặp món trong một bữa. Sai ⇒ 400 kèm lỗi theo ô.
     */
    private static MenuGrid editedGrid(MenuContext ctx, StoredOutput output, MenuAiDraftGridRequest request) {
        Map<LocalDate, Map<String, Long>> original = cellsOf(output);
        Map<String, MenuSlot> slots = new LinkedHashMap<>();
        ctx.slots().forEach(slot -> slots.put(slot.key(), slot));
        List<LocalDate> served = ctx.servedDates();
        List<FieldErrorResponse> errors = new ArrayList<>();
        Map<LocalDate, Map<String, Long>> cells = new TreeMap<>();
        for (MenuAiDraftGridRequest.Day day : request.days()) {
            String field = "days[" + day.date() + "]";
            if (!served.contains(day.date()) || cells.containsKey(day.date())) {
                errors.add(new FieldErrorResponse(field, "Ngày không thuộc các ngày phục vụ của tuần hoặc bị lặp"));
                continue;
            }
            Map<String, Long> bySlot = new LinkedHashMap<>();
            Map<MealSession, Set<Long>> usedBySession = new HashMap<>();
            for (MenuAiDraftGridRequest.Item item : day.items()) {
                MenuSlot slot = slots.get(item.slot());
                String cell = field + "." + item.slot();
                if (slot == null || bySlot.containsKey(item.slot())) {
                    errors.add(new FieldErrorResponse(cell, "Ô không thuộc khung thực đơn hoặc bị lặp"));
                    continue;
                }
                Long suggested = original.getOrDefault(day.date(), Map.of()).get(item.slot());
                if (!ctx.candidatesOf(slot).contains(item.dishId())
                        && !item.dishId().equals(suggested)) {
                    errors.add(new FieldErrorResponse(cell, "Món không thuộc danh sách chọn được của ô này"));
                } else if (!usedBySession
                        .computeIfAbsent(slot.session(), s -> new TreeSet<>())
                        .add(item.dishId())) {
                    errors.add(new FieldErrorResponse(cell, "Món đã có ở ô khác của cùng bữa"));
                }
                bySlot.put(item.slot(), item.dishId());
            }
            if (bySlot.size() != slots.size()) {
                errors.add(new FieldErrorResponse(field, "Mỗi ô của ngày cần đúng một món"));
            }
            cells.put(day.date(), bySlot);
        }
        if (cells.size() != served.size()) {
            errors.add(new FieldErrorResponse("days", "Cần đủ mọi ngày phục vụ của tuần"));
        }
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
        return new MenuGrid(cells, Map.of());
    }

    private static int editedCells(StoredOutput output, MenuGrid grid) {
        Map<LocalDate, Map<String, Long>> original = cellsOf(output);
        int edited = 0;
        for (Map.Entry<LocalDate, Map<String, Long>> day : grid.cells().entrySet()) {
            Map<String, Long> suggested = original.getOrDefault(day.getKey(), Map.of());
            for (Map.Entry<String, Long> cell : day.getValue().entrySet()) {
                if (!cell.getValue().equals(suggested.get(cell.getKey()))) {
                    edited++;
                }
            }
        }
        return edited;
    }

    // Postgres lưu tới micro giây: cắt sẵn để giá trị trả ngay sau khi lưu trùng giá trị đọc lại
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    // AUTH-20, NUT-08: lập thực đơn (kể cả nhờ AI gợi ý) cần MENU_MANAGE
    private static void checkCanManage(CurrentUser currentUser) {
        if (!currentUser.hasPermission(Permission.MENU_MANAGE)) {
            throw GlobalException.of(ApiCode.FORBIDDEN);
        }
    }

    private static void checkRequest(MenuAiDraftRequest request) {
        List<FieldErrorResponse> missing = new ArrayList<>();
        // Chỉnh từ mẫu và món bắt buộc là ràng buộc của cả tuần
        if (request.kind() != MenuAiDraftKind.MENU_WEEKLY) {
            if (request.strategy() == MenuAiStrategy.TEMPLATE_ADJUST) {
                missing.add(new FieldErrorResponse("strategy", "Chỉnh từ thực đơn mẫu chỉ dùng cho gợi ý tuần"));
            }
            if (request.requiredDishIds() != null && !request.requiredDishIds().isEmpty()) {
                missing.add(new FieldErrorResponse("requiredDishIds", "Món bắt buộc chỉ dùng cho gợi ý tuần"));
            }
        }
        if (request.kind() == MenuAiDraftKind.MENU_DAILY) {
            if (!missing.isEmpty()) {
                throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, missing);
            }
            return;
        }
        if (request.weekStart() == null) {
            missing.add(new FieldErrorResponse("weekStart", "Chọn tuần cần gợi ý"));
        }
        if (request.kind() == MenuAiDraftKind.MENU_DAY_FIX) {
            if (request.targetDate() == null) {
                missing.add(new FieldErrorResponse("targetDate", "Chọn ngày có món cần thay"));
            }
            if (request.targetSession() == null) {
                missing.add(new FieldErrorResponse("targetSession", "Chọn bữa có món cần thay"));
            }
            if (request.fromDishId() == null) {
                missing.add(new FieldErrorResponse("fromDishId", "Chọn món cần thay"));
            }
        }
        if (!missing.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, missing);
        }
        if (request.weekStart().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw GlobalException.of(ApiCode.MEAL_PLAN_WEEK_INVALID);
        }
    }

    private MenuAiDraft findDraft(Long id) {
        return draftRepository.findById(id).orElseThrow(() -> GlobalException.of(ApiCode.MENU_AI_DRAFT_NOT_FOUND));
    }

    // Đọc ngữ cảnh trong transaction ngắn, gọi engine (có thể gọi model) ngoài transaction
    private Suggestion suggest(CurrentUser user, MenuAiDraftRequest request) {
        MenuBlock block = request.block();
        return switch (request.kind()) {
            case MENU_WEEKLY -> {
                MenuAiContextLoader.Constraints constraints = new MenuAiContextLoader.Constraints(
                        idSet(request.unavailableFoodIds()),
                        idSet(request.requiredDishIds()),
                        request.strategy() == MenuAiStrategy.TEMPLATE_ADJUST,
                        true);
                // Vector (có gọi mạng) chạy ngoài transaction, trước khi dựng ứng viên; lỗi/tắt ⇒ rỗng, thứ tự như cũ
                Map<Long, Double> similarity = dishSimilarity.byWishes(request.wishes());
                MenuContext ctx = Objects.requireNonNull(transactionTemplate.execute(status -> contextLoader.weekly(
                        user,
                        block,
                        request.weekStart(),
                        request.season(),
                        request.wishes(),
                        constraints,
                        similarity)));
                MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);
                yield new Suggestion(ctx, outcome, gridOutput(ctx, outcome.grid()));
            }
            case MENU_DAILY -> {
                Map<Long, Double> similarity = dishSimilarity.byWishes(request.wishes());
                MenuContext ctx = Objects.requireNonNull(transactionTemplate.execute(status -> contextLoader.daily(
                        user,
                        block,
                        request.season(),
                        request.wishes(),
                        idSet(request.unavailableFoodIds()),
                        similarity)));
                MenuSuggestionEngine.Outcome outcome = engine.suggestDaily(ctx);
                yield new Suggestion(ctx, outcome, gridOutput(ctx, outcome.grid()));
            }
            case MENU_DAY_FIX -> {
                // Có mong muốn thì theo mong muốn; không thì món gần nghĩa món cần thay đứng trước
                Map<Long, Double> similarity = StringUtils.hasText(request.wishes())
                        ? dishSimilarity.byWishes(request.wishes())
                        : dishSimilarity.likeDish(request.fromDishId());
                MenuAiContextLoader.DayFix fix =
                        Objects.requireNonNull(transactionTemplate.execute(status -> contextLoader.dayFix(
                                user,
                                block,
                                request.weekStart(),
                                request.targetDate(),
                                request.targetSession(),
                                request.fromDishId(),
                                request.season(),
                                request.wishes(),
                                idSet(request.unavailableFoodIds()),
                                similarity)));
                MenuSuggestionEngine.Outcome outcome =
                        engine.suggestDayFix(fix.ctx(), fix.current(), fix.date(), fix.slot());
                yield new Suggestion(fix.ctx(), outcome, fixOutput(fix, outcome.grid()));
            }
        };
    }

    private MenuAiDraft toDraft(CurrentUser currentUser, MenuAiDraftRequest request, Suggestion suggestion) {
        MenuSuggestionEngine.Outcome outcome = suggestion.outcome();
        MenuContext ctx = suggestion.ctx();
        MenuAiDraft draft = MenuAiDraft.create(
                request.kind(),
                ctx.block(),
                request.kind() == MenuAiDraftKind.MENU_DAILY ? null : request.weekStart(),
                request.kind() == MenuAiDraftKind.MENU_DAY_FIX
                        ? new MenuAiDraft.Target(request.targetDate(), request.targetSession(), request.fromDishId())
                        : null,
                outcome.status(),
                outcome.source(),
                outcome.fallbackReason(),
                currentUser.userId(),
                now());
        MenuSuggestionEngine.ModelCall call = outcome.call();
        draft.recordModelCall(
                call.provider(), call.model(), call.promptVersion(), call.repairRounds(), call.latencyMs());
        MenuScore.Breakdown b = outcome.breakdown();
        StoredScore score = new StoredScore(
                outcome.aiScore(),
                outcome.baselineScore(),
                b == null ? null : new MenuAiDraftResponse.Breakdown(b.norm(), b.cost(), b.variety(), b.warnings()),
                outcome.checks());
        LocalDate monday = ctx.dates().getFirst().with(DayOfWeek.MONDAY);
        draft.recordContent(
                objectMapper.writeValueAsString(new StoredRequest(
                        ctx.season(),
                        ctx.wishes(),
                        contextLoader.historyFrom(monday),
                        monday.minusDays(1),
                        request.strategy() == null ? MenuAiStrategy.FREE : request.strategy(),
                        List.copyOf(idSet(request.unavailableFoodIds())),
                        List.copyOf(idSet(request.requiredDishIds())))),
                objectMapper.writeValueAsString(suggestion.output()),
                objectMapper.writeValueAsString(score),
                objectMapper.writeValueAsString(outcome.issues()),
                fingerprint(ctx.block(), ctx.dates().getLast(), dishIdsOf(suggestion.output())));
        return draft;
    }

    private static StoredOutput gridOutput(MenuContext ctx, MenuGrid grid) {
        List<StoredDay> days = new ArrayList<>();
        for (LocalDate date : ctx.dates()) {
            boolean off = ctx.offDays().contains(date);
            List<StoredItem> items = new ArrayList<>();
            for (MenuSlot slot : ctx.slots()) {
                Long dishId = grid.dish(date, slot.key());
                if (!off && dishId != null) {
                    items.add(new StoredItem(slot.key(), slot.session(), dishId));
                }
            }
            days.add(new StoredDay(date, off, grid.reasons().get(date), items));
        }
        return new StoredOutput(days, MenuPlanConstraints.changedCells(grid, ctx));
    }

    // Thay một món: chỉ lưu đúng ô được thay (một ngày, một món); rỗng khi không có phương án
    private static StoredOutput fixOutput(MenuAiContextLoader.DayFix fix, MenuGrid grid) {
        Long dishId = grid.dish(fix.date(), fix.slot().key());
        List<StoredItem> items = dishId == null
                ? List.of()
                : List.of(new StoredItem(fix.slot().key(), fix.slot().session(), dishId));
        return new StoredOutput(
                List.of(new StoredDay(fix.date(), false, grid.reasons().get(fix.date()), items)), List.of());
    }

    private static Set<Long> dishIdsOf(StoredOutput output) {
        return output.days().stream()
                .flatMap(d -> d.items().stream())
                .map(StoredItem::dishId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * SHA-256 của phiên bản dữ liệu nháp phụ thuộc: món trong nháp và thực phẩm của món (id + lần sửa), định mức hiệu
     * lực, bữa phục vụ của nhóm tuổi trong khối. Khác lúc dùng ⇒ phải kiểm lại.
     */
    private String fingerprint(MenuBlock block, LocalDate lastDay, Set<Long> dishIds) {
        List<AgeGroupInfo> groups = contextLoader.blockGroups(block);
        StringBuilder sb = new StringBuilder();
        groups.forEach(g -> sb.append("g:").append(g.id()).append(g.sessions()).append(';'));
        for (Dish dish : dishRepository.findByIdIn(dishIds).stream()
                .sorted(Comparator.comparing(Dish::getId))
                .toList()) {
            sb.append("d:")
                    .append(dish.getId())
                    .append(':')
                    .append(dish.getStatus())
                    .append(':')
                    .append(dish.getUpdatedAt())
                    .append(';');
            dish.getIngredients().stream()
                    .sorted(Comparator.comparing(
                                    (DishIngredient i) -> i.getFood().getId())
                            .thenComparing(DishIngredient::getAgeGroupId))
                    .forEach(i -> sb.append("i:")
                            .append(i.getAgeGroupId())
                            .append(':')
                            .append(i.getFood().getId())
                            .append(':')
                            .append(i.getGramsPerChild())
                            .append(':')
                            .append(i.getFood().getUpdatedAt())
                            .append(';'));
        }
        Set<Long> groupIds = groups.stream().map(AgeGroupInfo::id).collect(Collectors.toSet());
        evaluationSupport.configsUntil(lastDay).stream()
                .filter(c -> groupIds.contains(c.getAgeGroupId()))
                .sorted(Comparator.comparing(AgeGroupMealConfig::getId))
                .forEach(c -> sb.append("c:")
                        .append(c.getId())
                        .append(':')
                        .append(c.getUpdatedAt())
                        .append(';'));
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /**
     * Kiểm lại trên dữ liệu mới trước khi ghi (NUT-08, NUT-09): món ngừng dùng, bữa không còn phục vụ hoặc phát sinh lỗi
     * chặn ở {@code checkedDates} ⇒ 409 kèm danh sách lỗi theo ngày. {@code plans}: lưới của khối sẽ được ghi.
     * {@code blockingAllowed}: nháp vốn chưa đạt định mức lúc tạo nên không kiểm lại lỗi chặn (công bố vẫn chặn).
     */
    private void checkStillValid(
            MenuAiDraft draft, List<DayPlan> plans, Set<LocalDate> checkedDates, boolean blockingAllowed) {
        List<AgeGroupInfo> groups = contextLoader.blockGroups(draft.getMenuBlock());
        Set<MealSession> served =
                groups.stream().flatMap(g -> g.sessions().stream()).collect(Collectors.toSet());
        Set<Long> dishIds = plans.stream()
                .flatMap(d -> d.items().stream())
                .map(ItemPlan::dishId)
                .collect(Collectors.toSet());
        Map<Long, Dish> dishes =
                dishRepository.findByIdIn(dishIds).stream().collect(Collectors.toMap(Dish::getId, Function.identity()));
        List<FieldErrorResponse> errors = new ArrayList<>();
        for (DayPlan day : plans) {
            if (!checkedDates.contains(day.date())) {
                continue;
            }
            for (ItemPlan item : day.items()) {
                Dish dish = dishes.get(item.dishId());
                if (dish == null || dish.getStatus() != CatalogStatus.ACTIVE) {
                    errors.add(new FieldErrorResponse(
                            day.date().toString(),
                            "Món " + (dish == null ? "đã xóa" : dish.getName()) + " không còn dùng"));
                } else if (!served.contains(item.session())) {
                    errors.add(new FieldErrorResponse(
                            day.date().toString(), "Khối không còn phục vụ bữa " + item.session()));
                }
            }
        }
        if (errors.isEmpty() && !blockingAllowed) {
            MealPlanEvaluator.Result result = MealPlanEvaluator.evaluate(
                    plans,
                    groups,
                    MealPlanEvaluationSupport.dishInfos(dishes.values()),
                    evaluationSupport.normsUntil(draft.getWeekStart().plusDays(DAYS - 1L)));
            result.issues().stream()
                    .filter(i -> MealPlanEvaluator.BLOCKING.equals(i.severity()))
                    .filter(i -> i.date() == null || checkedDates.contains(i.date()))
                    .forEach(i -> errors.add(new FieldErrorResponse(
                            i.date() == null ? "week" : i.date().toString(), i.message())));
        }
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.MENU_AI_DRAFT_STALE, errors);
        }
    }

    /**
     * Tuần hiện có (giữ ngày nghỉ, ghi chú, món khối khác) + món của khối trong nháp thay cho món cũ của khối đó. Dữ
     * liệu đổi từ lúc tạo nháp (fingerprint khác) ⇒ kiểm lại cả tuần của khối.
     */
    private MealPlanRequest mergedWeek(
            LocalDate weekStart, MealPlanWeek week, MenuAiDraft draft, StoredOutput output, boolean edited) {
        // Lưới đã sửa: món đã được đối chiếu với ứng viên hiện tại, lỗi chặn được phép (công bố vẫn chặn — NUT-09)
        if (edited
                || !fingerprint(draft.getMenuBlock(), weekStart.plusDays(DAYS - 1L), dishIdsOf(output))
                        .equals(draft.getInputFingerprint())) {
            List<DayPlan> plans = output.days().stream()
                    .map(d -> new DayPlan(
                            d.date(),
                            d.off(),
                            d.items().stream()
                                    .map(i -> new ItemPlan(i.session(), draft.getMenuBlock(), i.dishId()))
                                    .distinct()
                                    .toList()))
                    .toList();
            checkStillValid(
                    draft,
                    plans,
                    plans.stream().map(DayPlan::date).collect(Collectors.toSet()),
                    edited || !feasible(draft));
        }
        Map<LocalDate, StoredDay> drafted =
                output.days().stream().collect(Collectors.toMap(StoredDay::date, Function.identity()));
        List<MealPlanRequest.DayInput> days = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            LocalDate date = weekStart.plusDays(i);
            MealPlanDay existing = week == null ? null : week.day(date).orElse(null);
            List<MealPlanRequest.ItemInput> items = new ArrayList<>();
            if (existing != null) {
                existing.getItems().stream()
                        .filter(item -> item.getMenuBlock() != draft.getMenuBlock())
                        .forEach(item -> items.add(new MealPlanRequest.ItemInput(
                                item.getSession(), item.getMenuBlock(), item.getDishId())));
            }
            StoredDay day = drafted.get(date);
            if (day != null) {
                day.items()
                        .forEach(item -> items.add(
                                new MealPlanRequest.ItemInput(item.session(), draft.getMenuBlock(), item.dishId())));
            }
            boolean off = existing != null ? existing.isOff() : day != null && day.off();
            days.add(new MealPlanRequest.DayInput(date, off, existing == null ? "" : existing.getNote(), items));
        }
        return new MealPlanRequest(days);
    }

    /**
     * Thay một món: món cũ phải còn đúng ô (chưa ai sửa tay), ngày đó sau khi thay không có lỗi chặn trên dữ liệu mới;
     * mọi ô khác của tuần giữ nguyên.
     */
    // Món cần thay của nháp phải còn ở đúng bữa, khối của ngày đó
    private static void checkTargetStillThere(MenuAiDraft draft, MealPlanWeek week) {
        LocalDate date = draft.getTargetDate();
        MealPlanDay day = week == null ? null : week.day(date).orElse(null);
        boolean stillThere = day != null
                && day.getItems().stream()
                        .anyMatch(i -> i.getMenuBlock() == draft.getMenuBlock()
                                && i.getSession() == draft.getTargetSession()
                                && i.getDishId().equals(draft.getFromDishId()));
        if (!stillThere) {
            throw GlobalException.withFieldErrors(
                    ApiCode.MENU_AI_DRAFT_STALE,
                    List.of(new FieldErrorResponse(date.toString(), "Món cần thay đã được đổi trên kế hoạch tuần")));
        }
    }

    // Tuần đã công bố: đổi đúng món cần thay trong ngày, giữ nguyên ghi chú và các món còn lại, rồi để sửa ngoại lệ có
    // sẵn
    // kiểm giờ chốt, lý do (≥10 ký tự), lưu lịch sử và báo phụ huynh/bếp. Cân đối chỉ cảnh báo khi sửa ngoại lệ.
    private MealPlanResponse amendWithDraft(
            CurrentUser user, MenuAiDraft draft, MealPlanWeek week, StoredOutput output, String reason) {
        checkTargetStillThere(draft, week);
        LocalDate date = draft.getTargetDate();
        Long toDishId = output.days().getFirst().items().getFirst().dishId();
        MealPlanDay day = week.day(date).orElseThrow();
        List<MealPlanRequest.ItemInput> items = day.getItems().stream()
                .sorted(Comparator.comparingInt(MealPlanItem::getSortOrder))
                .map(item -> {
                    boolean target = item.getMenuBlock() == draft.getMenuBlock()
                            && item.getSession() == draft.getTargetSession()
                            && item.getDishId().equals(draft.getFromDishId());
                    return new MealPlanRequest.ItemInput(
                            item.getSession(), item.getMenuBlock(), target ? toDishId : item.getDishId());
                })
                .toList();
        return mealPlanService.amendMealPlanDay(
                user, draft.getWeekStart(), date, new MealPlanAmendRequest(day.getNote(), items, reason));
    }

    private MealPlanRequest replacedDish(MenuAiDraft draft, MealPlanWeek week, StoredOutput output) {
        LocalDate date = draft.getTargetDate();
        checkTargetStillThere(draft, week);
        Long toDishId = output.days().getFirst().items().getFirst().dishId();
        List<MealPlanRequest.DayInput> days = new ArrayList<>();
        List<DayPlan> blockPlans = new ArrayList<>();
        for (MealPlanDay existing : week.getDays()) {
            List<MealPlanRequest.ItemInput> items = new ArrayList<>();
            List<ItemPlan> blockItems = new ArrayList<>();
            existing.getItems().stream()
                    .sorted(Comparator.comparingInt(MealPlanItem::getSortOrder))
                    .forEach(item -> {
                        boolean target = existing.getMenuDate().equals(date)
                                && item.getMenuBlock() == draft.getMenuBlock()
                                && item.getSession() == draft.getTargetSession()
                                && item.getDishId().equals(draft.getFromDishId());
                        Long dishId = target ? toDishId : item.getDishId();
                        items.add(new MealPlanRequest.ItemInput(item.getSession(), item.getMenuBlock(), dishId));
                        if (item.getMenuBlock() == draft.getMenuBlock()) {
                            blockItems.add(new ItemPlan(item.getSession(), item.getMenuBlock(), dishId));
                        }
                    });
            days.add(new MealPlanRequest.DayInput(existing.getMenuDate(), existing.isOff(), existing.getNote(), items));
            blockPlans.add(new DayPlan(existing.getMenuDate(), existing.isOff(), blockItems));
        }
        checkStillValid(draft, blockPlans, Set.of(date), false);
        return new MealPlanRequest(days);
    }

    // Hàng của bảng gợi ý suy từ chính lưới đã lưu (khóa ô + loại món đang nằm ở ô): nháp cũ vẫn đúng hàng dù khung ô
    // của bộ mẫu đã đổi sau đó
    private static List<MenuAiDraftResponse.Slot> slotsOf(StoredOutput output, Map<Long, Dish> dishes) {
        Map<String, MealSession> sessions = new LinkedHashMap<>();
        Map<String, Set<DishType>> types = new HashMap<>();
        for (StoredDay day : output.days()) {
            for (StoredItem item : day.items()) {
                sessions.putIfAbsent(item.slot(), item.session());
                Dish dish = dishes.get(item.dishId());
                if (dish != null) {
                    types.computeIfAbsent(item.slot(), k -> new TreeSet<>()).add(dish.getDishType());
                }
            }
        }
        return sessions.entrySet().stream()
                .map(e -> new MenuAiDraftResponse.Slot(
                        e.getKey(),
                        e.getValue(),
                        slotIndex(e.getKey()),
                        List.copyOf(types.getOrDefault(e.getKey(), Set.of()))))
                .sorted(Comparator.comparing(MenuAiDraftResponse.Slot::session)
                        .thenComparingInt(MenuAiDraftResponse.Slot::index))
                .toList();
    }

    // Khóa ô dạng "LUNCH.2" ⇒ chỉ số 1 (xem MenuSlot.key)
    private static int slotIndex(String key) {
        return Integer.parseInt(key.substring(key.lastIndexOf('.') + 1)) - 1;
    }

    // Có lưới món và không còn lỗi chặn của evaluator lúc tạo (NUT-09)
    private boolean feasible(MenuAiDraft draft) {
        return draft.getStatus() != MenuAiDraftStatus.NO_FEASIBLE
                && List.of(objectMapper.readValue(draft.getIssuesJson(), MealPlanResponse.Issue[].class)).stream()
                        .noneMatch(i -> MealPlanEvaluator.BLOCKING.equals(i.severity()));
    }

    private MenuAiDraftResponse toResponse(MenuAiDraft draft) {
        StoredOutput output = objectMapper.readValue(draft.getOutputJson(), StoredOutput.class);
        StoredScore score = objectMapper.readValue(draft.getScoreJson(), StoredScore.class);
        StoredRequest stored = objectMapper.readValue(draft.getRequestJson(), StoredRequest.class);
        List<MealPlanResponse.Issue> issues =
                List.of(objectMapper.readValue(draft.getIssuesJson(), MealPlanResponse.Issue[].class));
        List<MenuPlanConstraints.Changed> changed = output.changedCells() == null ? List.of() : output.changedCells();
        Set<Long> dishIds = new TreeSet<>(dishIdsOf(output));
        changed.stream()
                .map(MenuPlanConstraints.Changed::fromDishId)
                .filter(Objects::nonNull)
                .forEach(dishIds::add);
        Map<Long, Dish> dishes = dishRepository.findAllById(dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        Function<Long, String> nameOf = id ->
                id == null || !dishes.containsKey(id) ? null : dishes.get(id).getName();
        boolean daily = draft.getKind() == MenuAiDraftKind.MENU_DAILY;
        List<MenuAiDraftResponse.Day> days = output.days().stream()
                .map(d -> new MenuAiDraftResponse.Day(
                        daily ? null : d.date(),
                        d.off(),
                        d.reason(),
                        d.items().stream()
                                .map(i -> {
                                    Dish dish = dishes.get(i.dishId());
                                    return new MenuAiDraftResponse.Item(
                                            i.slot(),
                                            i.session(),
                                            i.dishId(),
                                            dish == null ? null : dish.getName(),
                                            dish == null ? null : dish.getDishType());
                                })
                                .toList()))
                .toList();
        return new MenuAiDraftResponse(
                draft.getId(),
                draft.getKind(),
                draft.getMenuBlock(),
                draft.getWeekStart(),
                draft.getTargetDate(),
                draft.getTargetSession(),
                draft.getFromDishId(),
                draft.getStatus(),
                feasible(draft),
                draft.getSource(),
                draft.getFallbackReason(),
                draft.getModel(),
                draft.getPromptVersion(),
                draft.getCreatedAt(),
                new MenuAiDraftResponse.Score(score.ai(), score.baseline(), score.breakdown()),
                slotsOf(output, dishes),
                days,
                issues,
                score.checks() == null ? List.of() : score.checks(),
                draft.getEditedCells(),
                stored.historyFrom(),
                stored.historyTo(),
                stored.strategy() == null ? MenuAiStrategy.FREE : stored.strategy(),
                changed.stream()
                        .map(c -> new MenuAiDraftResponse.ChangedCell(
                                c.date(),
                                c.slot(),
                                c.fromDishId(),
                                nameOf.apply(c.fromDishId()),
                                c.toDishId(),
                                nameOf.apply(c.toDishId())))
                        .toList());
    }
}
