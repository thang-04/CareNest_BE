package com.carenest.service.meals.impl;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
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
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuAiDraftKind;
import com.carenest.utils.enums.MenuAiDraftStatus;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final MenuSuggestionEngine engine;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    record StoredRequest(String season, String wishes) {}

    record StoredItem(String slot, MealSession session, Long dishId) {}

    record StoredDay(LocalDate date, boolean off, String reason, List<StoredItem> items) {}

    record StoredOutput(List<StoredDay> days) {}

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
    public MealPlanResponse applyDraft(CurrentUser currentUser, Long id) {
        String logPrefix = LOG_PREFIX + "|applyDraft|userId=" + currentUser.userId() + "|draftId=" + id;
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
            // NUT-08: tuần đã công bố không nhận nháp
            if (week != null && week.isPublished()) {
                throw GlobalException.of(ApiCode.MEAL_PLAN_PUBLISHED);
            }
            StoredOutput output = objectMapper.readValue(draft.getOutputJson(), StoredOutput.class);
            MealPlanRequest request = draft.getKind() == MenuAiDraftKind.MENU_DAY_FIX
                    ? replacedDish(draft, week, output)
                    : mergedWeek(weekStart, week, draft, output);
            MealPlanResponse response = mealPlanService.saveMealPlan(currentUser, weekStart, request);
            draft.apply(currentUser.userId(), now());
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
        if (request.kind() == MenuAiDraftKind.MENU_DAILY) {
            return;
        }
        List<FieldErrorResponse> missing = new ArrayList<>();
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
                MenuContext ctx = Objects.requireNonNull(transactionTemplate.execute(status ->
                        contextLoader.weekly(user, block, request.weekStart(), request.season(), request.wishes())));
                MenuSuggestionEngine.Outcome outcome = engine.suggestWeek(ctx);
                yield new Suggestion(ctx, outcome, gridOutput(ctx, outcome.grid()));
            }
            case MENU_DAILY -> {
                MenuContext ctx = Objects.requireNonNull(transactionTemplate.execute(
                        status -> contextLoader.daily(user, block, request.season(), request.wishes())));
                MenuSuggestionEngine.Outcome outcome = engine.suggestDaily(ctx);
                yield new Suggestion(ctx, outcome, gridOutput(ctx, outcome.grid()));
            }
            case MENU_DAY_FIX -> {
                MenuAiContextLoader.DayFix fix =
                        Objects.requireNonNull(transactionTemplate.execute(status -> contextLoader.dayFix(
                                user,
                                block,
                                request.weekStart(),
                                request.targetDate(),
                                request.targetSession(),
                                request.fromDishId(),
                                request.season(),
                                request.wishes())));
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
        draft.recordContent(
                objectMapper.writeValueAsString(new StoredRequest(ctx.season(), ctx.wishes())),
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
        return new StoredOutput(days);
    }

    // Thay một món: chỉ lưu đúng ô được thay (một ngày, một món); rỗng khi không có phương án
    private static StoredOutput fixOutput(MenuAiContextLoader.DayFix fix, MenuGrid grid) {
        Long dishId = grid.dish(fix.date(), fix.slot().key());
        List<StoredItem> items = dishId == null
                ? List.of()
                : List.of(new StoredItem(fix.slot().key(), fix.slot().session(), dishId));
        return new StoredOutput(
                List.of(new StoredDay(fix.date(), false, grid.reasons().get(fix.date()), items)));
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
     */
    private void checkStillValid(MenuAiDraft draft, List<DayPlan> plans, Set<LocalDate> checkedDates) {
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
        if (errors.isEmpty()) {
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
    private MealPlanRequest mergedWeek(LocalDate weekStart, MealPlanWeek week, MenuAiDraft draft, StoredOutput output) {
        if (!fingerprint(draft.getMenuBlock(), weekStart.plusDays(DAYS - 1L), dishIdsOf(output))
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
            checkStillValid(draft, plans, plans.stream().map(DayPlan::date).collect(Collectors.toSet()));
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
    private MealPlanRequest replacedDish(MenuAiDraft draft, MealPlanWeek week, StoredOutput output) {
        LocalDate date = draft.getTargetDate();
        MealPlanDay day = week == null ? null : week.day(date).orElse(null);
        boolean stillThere = day != null
                && day.getItems().stream()
                        .anyMatch(i -> i.getMenuBlock() == draft.getMenuBlock()
                                && i.getSession() == draft.getTargetSession()
                                && i.getDishId().equals(draft.getFromDishId()));
        Long toDishId = output.days().getFirst().items().getFirst().dishId();
        if (!stillThere) {
            throw GlobalException.withFieldErrors(
                    ApiCode.MENU_AI_DRAFT_STALE,
                    List.of(new FieldErrorResponse(date.toString(), "Món cần thay đã được đổi trên kế hoạch tuần")));
        }
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
        checkStillValid(draft, blockPlans, Set.of(date));
        return new MealPlanRequest(days);
    }

    private MenuAiDraftResponse toResponse(MenuAiDraft draft) {
        StoredOutput output = objectMapper.readValue(draft.getOutputJson(), StoredOutput.class);
        StoredScore score = objectMapper.readValue(draft.getScoreJson(), StoredScore.class);
        List<MealPlanResponse.Issue> issues =
                List.of(objectMapper.readValue(draft.getIssuesJson(), MealPlanResponse.Issue[].class));
        Map<Long, Dish> dishes = dishRepository.findAllById(dishIdsOf(output)).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
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
                draft.getSource(),
                draft.getFallbackReason(),
                draft.getModel(),
                draft.getPromptVersion(),
                draft.getCreatedAt(),
                new MenuAiDraftResponse.Score(score.ai(), score.baseline(), score.breakdown()),
                days,
                issues,
                score.checks() == null ? List.of() : score.checks());
    }
}
