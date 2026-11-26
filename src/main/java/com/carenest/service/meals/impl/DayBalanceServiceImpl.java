package com.carenest.service.meals.impl;

import com.carenest.dto.meals.DayBalanceRequest;
import com.carenest.dto.meals.DayBalanceResponse;
import com.carenest.dto.meals.DayBalanceStatusResponse;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.entity.meals.DayBalance;
import com.carenest.entity.meals.DayBalanceCount;
import com.carenest.entity.meals.DayBalanceLine;
import com.carenest.entity.meals.Food;
import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.DayBalanceRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.DayBalanceService;
import com.carenest.service.meals.impl.DayBalanceCalculator.FoodMeta;
import com.carenest.service.meals.impl.DayBalanceCalculator.Line;
import com.carenest.service.meals.impl.DayBalanceReader.Draft;
import com.carenest.service.meals.impl.DayBalanceReader.Source;
import com.carenest.service.organization.CampusService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.Permission;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DayBalanceServiceImpl implements DayBalanceService {
    private static final String LOG_PREFIX = "[DayBalanceServiceImpl]";
    private static final int MAX_RANGE_DAYS = 31;
    private static final int QUANTITY_SCALE = 3;
    private static final BigDecimal QUANTITY_STEP = BigDecimal.ONE.movePointLeft(QUANTITY_SCALE);
    private static final List<BigDecimal> STEPS = List.of(new BigDecimal("0.1"), new BigDecimal("0.5"), BigDecimal.ONE);

    private final DayBalanceReader reader;
    private final DayBalanceRepository balanceRepository;
    private final MealPlanWeekRepository weekRepository;
    private final CampusService campusService;
    private final BoardingWriteGuard writeGuard;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<DayBalanceStatusResponse> getStatuses(CurrentUser currentUser, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getStatuses|userId=" + currentUser.userId() + "|from=" + from;
        log.info("{}|START", logPrefix);
        try {
            if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
                throw GlobalException.of(ApiCode.DAY_BALANCE_INPUT_INVALID, "khoảng ngày tối đa 31 ngày");
            }
            List<DayBalanceStatusResponse> response = from.datesUntil(to.plusDays(1))
                    .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY)
                    .map(this::statusOf)
                    .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private DayBalanceStatusResponse statusOf(LocalDate date) {
        Source source = reader.load(date, DayBalanceReader.ROUND_EXPECTED);
        if (source.dayOff()) {
            return new DayBalanceStatusResponse(date, true, source.offReason(), false, false, false, List.of());
        }
        if (source.saved() == null) {
            return new DayBalanceStatusResponse(date, false, null, false, false, false, List.of("Chưa cân đối"));
        }
        boolean stale = reader.stale(source);
        MealPlanEvaluator.Result result = reader.evaluate(source, reader.compose(source, true));
        List<String> problems = new ArrayList<>();
        if (stale) {
            problems.add("Thực đơn đã đổi, cần cân đối lại");
        }
        problems.addAll(DayBalanceReader.problems(result));
        return new DayBalanceStatusResponse(
                date, false, null, true, stale, !stale && DayBalanceReader.passed(source, result), problems);
    }

    @Override
    @Transactional(readOnly = true)
    public DayBalanceResponse getBalance(CurrentUser currentUser, LocalDate date, int round) {
        String logPrefix = LOG_PREFIX + "|getBalance|userId=" + currentUser.userId() + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            Source source = reader.load(date, checkRound(round));
            DayBalanceResponse response = toResponse(currentUser, source, reader.compose(source, true), round);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DayBalanceResponse previewBalance(
            CurrentUser currentUser, LocalDate date, int round, DayBalanceRequest request) {
        String logPrefix = LOG_PREFIX + "|previewBalance|userId=" + currentUser.userId() + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            Source source = reader.load(date, checkRound(round));
            DayBalanceResponse response = toResponse(currentUser, source, apply(source, request), round);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public DayBalanceResponse saveBalance(
            CurrentUser currentUser, LocalDate date, int round, DayBalanceRequest request) {
        String logPrefix = LOG_PREFIX + "|saveBalance|userId=" + currentUser.userId() + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            short balanceRound = checkRound(round);
            // Khóa dòng tuần: lưu cân đối và công bố tuần không chen nhau
            weekRepository.findForUpdateByWeekStart(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            Source source = reader.load(date, balanceRound);
            checkEditable(source);
            DayBalance balance = source.saved();
            Long current = balance == null ? null : balance.getRowVersion();
            if (current == null ? request.version() != null : !current.equals(request.version())) {
                throw GlobalException.of(ApiCode.DAY_BALANCE_CONFLICT, date);
            }
            Draft draft = apply(source, request);
            if (balance == null) {
                balance = DayBalance.of(date, balanceRound);
            }
            balance.replace(
                    draft.counts(),
                    draft.lines().stream().map(l -> toEntity(l, draft)).toList(),
                    draft.roundingStep(),
                    currentUser.userId(),
                    clock.instant());
            balanceRepository.saveAndFlush(balance);
            // Kho khô đã trừ theo bản trước thì tính lại theo bản mới (sau commit)
            events.publishEvent(new DayBalanceSavedEvent(date));
            Source saved = reader.load(date, balanceRound);
            DayBalanceResponse response = toResponse(currentUser, saved, reader.compose(saved, true), round);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static DayBalanceLine toEntity(Line line, Draft draft) {
        FoodMeta meta = draft.foods().get(line.foodId());
        return new DayBalanceLine(
                line.ageGroupId(),
                line.session(),
                line.dishId(),
                line.foodId(),
                line.baseGrams(),
                line.grams(),
                meta.wastePct(),
                draft.prices().get(line.foodId()),
                meta.purchaseUnit(),
                meta.unitSize());
    }

    // Số gửi lên chỉ ghi đè ô đang có trong thực đơn: không thêm được món hay thực phẩm ngoài thực đơn
    private Draft apply(Source source, DayBalanceRequest request) {
        Draft draft = reader.compose(source, false, request.fromRecipe());
        Map<String, DayBalanceRequest.CountInput> counts = new HashMap<>();
        // Lần 2 luôn theo suất đã chốt, không nhận số trẻ nhập tay
        if (source.round() == DayBalanceReader.ROUND_CONFIRMED) {
            draft = draft.withCounts(confirmedCounts(source, draft.counts()));
        } else if (request.counts() != null) {
            request.counts().forEach(c -> counts.put(c.campusId() + "|" + c.ageGroupId(), c));
            boolean unknown = request.counts().stream()
                    .anyMatch(c -> !source.campusIds().contains(c.campusId())
                            || source.groups().stream().noneMatch(g -> g.id().equals(c.ageGroupId())));
            if (unknown) {
                throw GlobalException.of(
                        ApiCode.DAY_BALANCE_INPUT_INVALID, "số trẻ gửi cho điểm trường hoặc nhóm tuổi không có");
            }
            // NUT-35: số trẻ dự kiến không vượt tổng trẻ đang học của nhóm tuổi tại điểm trường
            Map<Long, Map<Long, Integer>> enrolledByCampus = new HashMap<>();
            for (DayBalanceRequest.CountInput c : request.counts()) {
                int enrolled = enrolledByCampus
                        .computeIfAbsent(c.campusId(), id -> reader.enrolledCounts(id, source.date()))
                        .getOrDefault(c.ageGroupId(), 0);
                if (c.children() > enrolled) {
                    throw GlobalException.of(
                            ApiCode.DAY_BALANCE_INPUT_INVALID,
                            "số trẻ dự kiến " + c.children() + " vượt tổng trẻ đang học " + enrolled);
                }
            }
        }
        List<DayBalanceCount> mergedCounts = draft.counts().stream()
                .map(c -> {
                    DayBalanceRequest.CountInput input = counts.get(c.campusId() + "|" + c.ageGroupId());
                    return input == null ? c : new DayBalanceCount(c.campusId(), c.ageGroupId(), input.children());
                })
                .toList();
        Map<String, DayBalanceRequest.LineInput> inputs = new HashMap<>();
        if (request.lines() != null) {
            request.lines()
                    .forEach(l -> inputs.put(key(l.ageGroupId(), l.session().name(), l.dishId(), l.foodId()), l));
        }
        List<Line> lines = draft.lines().stream()
                .map(l -> {
                    DayBalanceRequest.LineInput input =
                            inputs.get(key(l.ageGroupId(), l.session().name(), l.dishId(), l.foodId()));
                    return input == null
                            ? l
                            : new Line(
                                    l.ageGroupId(),
                                    l.session(),
                                    l.dishId(),
                                    l.foodId(),
                                    input.baseGrams(),
                                    input.grams());
                })
                .toList();
        if (request.roundingStep() != null && STEPS.stream().noneMatch(s -> s.compareTo(request.roundingStep()) == 0)) {
            throw GlobalException.of(ApiCode.DAY_BALANCE_INPUT_INVALID, "bước làm tròn chỉ nhận 0,1; 0,5 hoặc 1");
        }
        // Không gửi số nào mới thì bản đã làm tròn vẫn là bản đã làm tròn
        boolean unchanged = request.counts() == null && request.lines() == null && request.edits() == null;
        BigDecimal step = request.roundingStep() == null && unchanged ? draft.roundingStep() : request.roundingStep();
        Draft result = new Draft(mergedCounts, lines, draft.foods(), draft.prices(), step);
        if (request.edits() != null) {
            for (DayBalanceRequest.EditInput edit : request.edits()) {
                result = applyEdit(result, edit);
            }
        }
        if (request.applyRounding()) {
            if (request.roundingStep() == null) {
                throw GlobalException.of(ApiCode.DAY_BALANCE_INPUT_INVALID, "chọn bước làm tròn");
            }
            result = result.withLines(DayBalanceCalculator.round(
                    result.lines(), result.childrenByAgeGroup(), result.foods(), request.roundingStep()));
        }
        return result;
    }

    private List<DayBalanceCount> confirmedCounts(Source source, List<DayBalanceCount> counts) {
        Map<Long, Map<Long, Integer>> byCampus = new HashMap<>();
        return counts.stream()
                .map(c -> new DayBalanceCount(
                        c.campusId(),
                        c.ageGroupId(),
                        byCampus.computeIfAbsent(c.campusId(), id -> reader.confirmedCounts(id, source.date()))
                                .getOrDefault(c.ageGroupId(), 0)))
                .toList();
    }

    private static Draft applyEdit(Draft draft, DayBalanceRequest.EditInput edit) {
        if ((edit.gramsPerChild() == null) == (edit.purchaseQuantity() == null)) {
            throw GlobalException.of(ApiCode.DAY_BALANCE_INPUT_INVALID, "mỗi lần sửa chọn gam mỗi trẻ hoặc lượng mua");
        }
        boolean inMenu = draft.lines().stream()
                .anyMatch(l ->
                        l.ageGroupId().equals(edit.ageGroupId()) && l.foodId().equals(edit.foodId()));
        if (!inMenu) {
            throw GlobalException.of(
                    ApiCode.DAY_BALANCE_INPUT_INVALID, "thực phẩm cần sửa không có trong món của nhóm tuổi này");
        }
        BigDecimal grams = edit.gramsPerChild();
        if (grams == null) {
            FoodMeta food = draft.foods().get(edit.foodId());
            int children = draft.childrenByAgeGroup().getOrDefault(edit.ageGroupId(), 0);
            if (food == null || food.unitSize() == null || children <= 0) {
                throw GlobalException.of(
                        ApiCode.DAY_BALANCE_INPUT_INVALID, "cần có số trẻ và đơn vị mua để sửa lượng mua");
            }
            grams = DayBalanceCalculator.gramsForPurchase(
                    edit.purchaseQuantity().multiply(food.unitSize()), children, food.wastePct());
        }
        return draft.withLines(DayBalanceCalculator.setGrams(draft.lines(), edit.ageGroupId(), edit.foodId(), grams));
    }

    private static String key(Long ageGroupId, String session, Long dishId, Long foodId) {
        return ageGroupId + "|" + session + "|" + dishId + "|" + foodId;
    }

    // Lần 2 cần lần 1, mọi điểm trường đã chốt suất và biên bản kiểm thực của ngày chưa ký (INS-03)
    private String blockedReason(Source source) {
        if (source.round() != DayBalanceReader.ROUND_CONFIRMED) {
            return null;
        }
        if (source.seed() == null) {
            return "chưa có cân đối lần 1";
        }
        if (!reader.allConfirmed(source)) {
            return "chờ các điểm trường xác nhận suất ăn";
        }
        boolean signed = source.campusIds().stream()
                .anyMatch(
                        campusId -> writeGuard.signed(campusId, source.date(), InspectionTab.FRESH, InspectionTab.DRY));
        return signed ? "biên bản kiểm thực đã ký, sửa lại biên bản để cân đối" : null;
    }

    private void checkEditable(Source source) {
        if (source.date().isBefore(LocalDate.now(clock))) {
            throw GlobalException.of(ApiCode.DAY_BALANCE_LOCKED, source.date(), "ngày đã qua");
        }
        if (source.dayOff()) {
            throw GlobalException.of(
                    ApiCode.DAY_BALANCE_LOCKED, source.date(), "ngày nghỉ (" + source.offReason() + ")");
        }
        if (source.items().isEmpty()) {
            throw GlobalException.of(ApiCode.DAY_BALANCE_NO_MENU, source.date());
        }
        String blocked = blockedReason(source);
        if (blocked != null) {
            throw GlobalException.of(ApiCode.DAY_BALANCE_LOCKED, source.date(), blocked);
        }
    }

    private static short checkRound(int round) {
        if (round != DayBalanceReader.ROUND_EXPECTED && round != DayBalanceReader.ROUND_CONFIRMED) {
            throw GlobalException.of(ApiCode.DAY_BALANCE_INPUT_INVALID, "chỉ có lần 1 và lần 2");
        }
        return (short) round;
    }

    // AUTH-20, NUT-08: cân đối khẩu phần cùng quyền với lập thực đơn
    private static void checkCanManage(CurrentUser currentUser) {
        if (!currentUser.hasPermission(Permission.MENU_MANAGE)) {
            throw GlobalException.of(ApiCode.FORBIDDEN);
        }
    }

    private DayBalanceResponse toResponse(CurrentUser currentUser, Source source, Draft draft, int round) {
        MealPlanEvaluator.Result result = reader.evaluate(source, draft);
        boolean stale = reader.stale(source);
        Map<Long, Map<Long, Integer>> enrolledByCampus = new HashMap<>();
        Map<Long, String> campusNames = campusService.getCampuses(currentUser, null).stream()
                .collect(Collectors.toMap(CampusResponse::id, CampusResponse::name));
        Map<Long, String> dishNames = DayBalanceReader.dishNames(source);
        List<Food> foods = DayBalanceReader.usedFoods(source, draft);
        boolean open =
                !source.date().isBefore(LocalDate.now(clock)) && !source.items().isEmpty();
        String blocked = open ? blockedReason(source) : null;
        boolean canEdit = currentUser.hasPermission(Permission.MENU_MANAGE) && open && blocked == null;
        DayBalance saved = source.saved();
        return new DayBalanceResponse(
                source.date(),
                round,
                saved != null,
                saved == null ? null : saved.getRowVersion(),
                saved == null ? null : saved.getSavedAt(),
                stale,
                canEdit,
                source.dayOff(),
                source.offReason(),
                draft.roundingStep(),
                DayBalanceReader.passed(source, result),
                source.campusIds().stream()
                        .map(id -> new DayBalanceResponse.Campus(id, campusNames.getOrDefault(id, "")))
                        .toList(),
                source.groups().stream()
                        .map(g -> new DayBalanceResponse.AgeGroup(g.id(), g.name(), g.block(), g.sessions()))
                        .toList(),
                draft.counts().stream()
                        .map(c -> new DayBalanceResponse.Count(
                                c.campusId(),
                                c.ageGroupId(),
                                c.children(),
                                enrolledByCampus
                                        .computeIfAbsent(c.campusId(), id -> reader.enrolledCounts(id, source.date()))
                                        .getOrDefault(c.ageGroupId(), 0)))
                        .toList(),
                draft.lines().stream()
                        .map(l -> new DayBalanceResponse.Line(
                                l.ageGroupId(),
                                l.session(),
                                l.dishId(),
                                dishNames.get(l.dishId()),
                                l.foodId(),
                                l.baseGrams(),
                                l.grams()))
                        .toList(),
                foods.stream()
                        .map(f -> {
                            FoodMeta meta = draft.foods().get(f.getId());
                            return new DayBalanceResponse.Food(
                                    f.getId(),
                                    f.getCode(),
                                    f.getName(),
                                    meta.storage(),
                                    meta.wastePct(),
                                    meta.purchaseUnit(),
                                    meta.unitSize(),
                                    draft.prices().get(f.getId()),
                                    f.getUnit());
                        })
                        .toList(),
                groupResults(source, draft, result, foods),
                summary(source, draft, foods),
                blocked);
    }

    private static List<DayBalanceResponse.GroupResult> groupResults(
            Source source, Draft draft, MealPlanEvaluator.Result result, List<Food> foods) {
        Map<Long, Integer> children = draft.childrenByAgeGroup();
        Map<Long, MealPlanResponse.Evaluation> evaluations = result.evaluations().stream()
                .collect(Collectors.toMap(MealPlanResponse.Evaluation::ageGroupId, e -> e));
        return source.groups().stream()
                .map(group -> {
                    int count = children.getOrDefault(group.id(), 0);
                    List<DayBalanceResponse.FoodRow> rows = new ArrayList<>();
                    for (Food food : foods) {
                        List<Line> lines = draft.lines().stream()
                                .filter(l -> l.ageGroupId().equals(group.id())
                                        && l.foodId().equals(food.getId()))
                                .toList();
                        if (lines.isEmpty()) {
                            continue;
                        }
                        FoodMeta meta = draft.foods().get(food.getId());
                        BigDecimal perChild = lines.stream().map(Line::grams).reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal total = perChild.multiply(BigDecimal.valueOf(count));
                        BigDecimal purchase = MealDemandCalculator.purchased(total, meta.wastePct())
                                .setScale(2, RoundingMode.HALF_UP);
                        rows.add(new DayBalanceResponse.FoodRow(
                                food.getId(),
                                perChild,
                                total.setScale(2, RoundingMode.HALF_UP),
                                purchase,
                                quantity(purchase, meta),
                                cost(purchase, meta, draft.prices().get(food.getId()))));
                    }
                    return new DayBalanceResponse.GroupResult(
                            group.id(),
                            count,
                            evaluations.get(group.id()),
                            rows.stream()
                                    .map(DayBalanceResponse.FoodRow::cost)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                            rows);
                })
                .toList();
    }

    private static List<DayBalanceResponse.SummaryRow> summary(Source source, Draft draft, List<Food> foods) {
        Map<Long, Integer> children = draft.childrenByAgeGroup();
        return foods.stream()
                .map(food -> {
                    FoodMeta meta = draft.foods().get(food.getId());
                    BigDecimal total = DayBalanceCalculator.purchaseTotal(
                            draft.lines(), children, food.getId(), meta, draft.roundingStep());
                    Map<Long, BigDecimal> shares = DayBalanceReader.campusShares(source, draft, food.getId(), total);
                    BigDecimal quantity = quantity(total, meta);
                    // Chia lại số đã làm tròn toàn trường để phần các điểm trường cộng đúng số toàn trường
                    Map<Long, BigDecimal> quantities = quantity == null
                            ? Map.of()
                            : DayBalanceCalculator.allocate(quantity, shares, QUANTITY_STEP);
                    return new DayBalanceResponse.SummaryRow(
                            food.getId(),
                            total,
                            quantity,
                            cost(total, meta, draft.prices().get(food.getId())),
                            shares.entrySet().stream()
                                    .map(e -> new DayBalanceResponse.CampusShare(
                                            e.getKey(), e.getValue(), quantities.get(e.getKey())))
                                    .toList());
                })
                .toList();
    }

    private static BigDecimal quantity(BigDecimal purchase, FoodMeta meta) {
        return meta.unitSize() == null || meta.unitSize().signum() <= 0
                ? null
                : purchase.divide(meta.unitSize(), QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal cost(BigDecimal purchase, FoodMeta meta, BigDecimal price) {
        return price == null || meta.unitSize() == null || meta.unitSize().signum() <= 0
                ? BigDecimal.ZERO
                : price.multiply(purchase).divide(meta.unitSize(), 0, RoundingMode.HALF_UP);
    }
}
