package com.carenest.service.meals.impl;

import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.DayBalance;
import com.carenest.entity.meals.DayBalanceCount;
import com.carenest.entity.meals.DayBalanceLine;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.repository.meals.DayBalanceRepository;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.service.meals.FoodPriceService;
import com.carenest.service.meals.FoodPriceService.FoodPriceBook;
import com.carenest.service.meals.impl.DayBalanceCalculator.FoodMeta;
import com.carenest.service.meals.impl.DayBalanceCalculator.Line;
import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DayPlan;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.ItemPlan;
import com.carenest.service.meals.impl.MealPlanEvaluator.Portion;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Dựng bản cân đối khẩu phần của một ngày từ thực đơn, công thức món và bản đã lưu, rồi chấm lượng, chất, tiền. Dùng
 * chung cho màn cân đối, điều kiện công bố và các nơi lấy nhu cầu thực phẩm.
 */
@Component
@RequiredArgsConstructor
class DayBalanceReader {

    static final short ROUND_EXPECTED = 1;
    static final short ROUND_CONFIRMED = 2;
    private static final String OK = "OK";
    private static final BigDecimal DEFAULT_STEP = new BigDecimal("0.1");

    private final DayBalanceRepository balanceRepository;
    private final MealPlanWeekRepository weekRepository;
    private final FoodRepository foodRepository;
    private final MealCountRepository mealCountRepository;
    private final MealDemandCalculator demandCalculator;
    private final MealPlanEvaluationSupport evaluationSupport;
    private final FoodPriceService foodPriceService;
    private final ClassroomService classroomService;
    private final SchoolYearService schoolYearService;

    /** Món của một nhóm tuổi trong một bữa. */
    record DishKey(Long ageGroupId, MealSession session, Long dishId) {
        static DishKey of(Line line) {
            return new DishKey(line.ageGroupId(), line.session(), line.dishId());
        }
    }

    /** Dữ liệu nguồn của một ngày: thực đơn, nhóm tuổi được kiểm, món, thực phẩm và bản đã lưu. */
    record Source(
            LocalDate date,
            MealPlanDay day,
            List<AgeGroupInfo> groups,
            Map<Long, Dish> dishes,
            Map<Long, Food> foods,
            List<Long> campusIds,
            DayBalance saved,
            short round,
            DayBalance seed,
            String offReason) {

        /** Ngày nghỉ: tuần đánh dấu nghỉ hoặc rơi vào cuối tuần, lịch nghỉ của trường. */
        boolean dayOff() {
            return offReason != null;
        }

        List<MealPlanItem> items() {
            return day == null || dayOff() ? List.of() : day.getItems();
        }
    }

    /** Nội dung đang xét của bản cân đối: số trẻ, dòng gam và đơn vị mua, giá áp cho từng thực phẩm. */
    record Draft(
            List<DayBalanceCount> counts,
            List<Line> lines,
            Map<Long, FoodMeta> foods,
            Map<Long, BigDecimal> prices,
            BigDecimal roundingStep) {

        Draft withCounts(List<DayBalanceCount> newCounts) {
            return new Draft(newCounts, lines, foods, prices, roundingStep);
        }

        Draft withLines(List<Line> newLines) {
            return new Draft(counts, newLines, foods, prices, roundingStep);
        }

        Draft withRoundingStep(BigDecimal step) {
            return new Draft(counts, lines, foods, prices, step);
        }

        Map<Long, Integer> childrenByAgeGroup() {
            Map<Long, Integer> children = new HashMap<>();
            counts.forEach(c -> children.merge(c.ageGroupId(), c.children(), Integer::sum));
            return children;
        }
    }

    // Lý do ngày không phục vụ bữa; null = ngày học. Tuần đánh dấu nghỉ thắng, rồi tới cuối tuần, lịch nghỉ của trường
    private String offReasonOf(LocalDate date, MealPlanDay day) {
        if (day != null && day.isOff()) {
            return day.getNote().isBlank()
                    ? "Ngày nghỉ theo kế hoạch tuần"
                    : day.getNote().trim();
        }
        if (schoolYearService.isSchoolDay(date)) {
            return null;
        }
        if (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            return "Cuối tuần";
        }
        return schoolYearService.holidayName(date).orElse("Nghỉ theo lịch của trường");
    }

    boolean saved(LocalDate date, short round) {
        return balanceRepository.existsByBalanceDateAndRound(date, round);
    }

    /** Chia tổng lượng mua của một thực phẩm cho điểm trường theo số trẻ từng nhóm tuổi ở đó × gam mỗi trẻ. */
    static Map<Long, BigDecimal> campusShares(Source source, Draft draft, Long foodId, BigDecimal total) {
        Map<Long, BigDecimal> gramsByGroup = new HashMap<>();
        draft.lines().stream()
                .filter(l -> l.foodId().equals(foodId))
                .forEach(l -> gramsByGroup.merge(l.ageGroupId(), l.grams(), BigDecimal::add));
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        source.campusIds().forEach(id -> weights.put(id, BigDecimal.ZERO));
        draft.counts()
                .forEach(c -> weights.merge(
                        c.campusId(),
                        gramsByGroup
                                .getOrDefault(c.ageGroupId(), BigDecimal.ZERO)
                                .multiply(BigDecimal.valueOf(c.children())),
                        BigDecimal::add));
        return DayBalanceCalculator.allocate(
                total, weights, DayBalanceCalculator.stepOf(draft.foods().get(foodId), draft.roundingStep()));
    }

    Source load(LocalDate date, short round) {
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        MealPlanWeek week = weekRepository.findByWeekStart(weekStart).orElse(null);
        MealPlanDay day = week == null ? null : week.day(date).orElse(null);
        List<AgeGroupInfo> groups = week != null && week.isPublished()
                ? evaluationSupport.evaluatedGroups()
                : evaluationSupport.planningGroups();
        Map<Long, Dish> dishes = day == null ? Map.of() : demandCalculator.dishesOf(List.of(day));
        DayBalance saved =
                balanceRepository.findByBalanceDateAndRound(date, round).orElse(null);
        // Lần 2 chưa lưu thì xuất phát từ gam của lần 1
        DayBalance seed = round == ROUND_CONFIRMED
                ? balanceRepository
                        .findByBalanceDateAndRound(date, ROUND_EXPECTED)
                        .orElse(null)
                : null;
        Map<Long, Food> foods = new LinkedHashMap<>();
        dishes.values()
                .forEach(dish ->
                        dish.getIngredients().forEach(i -> foods.put(i.getFood().getId(), i.getFood())));
        DayBalance stored = saved != null ? saved : seed;
        if (stored != null) {
            // Công thức món đổi sau khi lưu: thực phẩm cũ vẫn nằm trong bản cân đối
            Set<Long> missing = stored.getLines().stream()
                    .map(DayBalanceLine::foodId)
                    .filter(id -> !foods.containsKey(id))
                    .collect(Collectors.toSet());
            foodRepository.findAllById(missing).forEach(f -> foods.put(f.getId(), f));
        }
        List<Long> campusIds = classroomService.findActiveYearClassrooms().stream()
                .map(ClassroomInfoResponse::campusId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return new Source(date, day, groups, dishes, foods, campusIds, saved, round, seed, offReasonOf(date, day));
    }

    /**
     * Bản cân đối theo thực đơn hiện tại: món đã có trong bản lưu giữ số đã lưu, món mới lấy theo công thức, món bị bỏ
     * thì mất dòng. {@code snapshot}: dùng thải bỏ, giá, đơn vị mua đã chụp lúc lưu (để xem lại số đã chốt).
     */
    Draft compose(Source source, boolean snapshot) {
        return compose(source, snapshot, false);
    }

    /** {@code fromRecipe}: gam lấy lại từ công thức món, chỉ giữ số trẻ đã lưu. */
    Draft compose(Source source, boolean snapshot, boolean fromRecipe) {
        Map<DishKey, List<Line>> saved = new LinkedHashMap<>();
        DayBalance stored = source.saved() != null ? source.saved() : source.seed();
        boolean seeding = source.saved() == null && source.seed() != null;
        if (stored != null && !fromRecipe) {
            stored.getLines().stream()
                    .sorted(Comparator.comparing(DayBalanceLine::foodId))
                    .forEach(l -> {
                        // Dựng lần 2 từ gam trước làm tròn của lần 1 để không cộng dồn phần làm tròn
                        Line line = new Line(
                                l.ageGroupId(),
                                l.session(),
                                l.dishId(),
                                l.foodId(),
                                l.baseGrams(),
                                seeding ? l.baseGrams() : l.grams());
                        saved.computeIfAbsent(DishKey.of(line), k -> new ArrayList<>())
                                .add(line);
                    });
        }
        List<Line> lines = new ArrayList<>();
        expected(source).forEach((key, recipe) -> lines.addAll(saved.getOrDefault(key, recipe)));

        FoodPriceBook book = foodPriceService.book(source.foods().keySet());
        Map<Long, FoodMeta> metas = new HashMap<>();
        Map<Long, BigDecimal> prices = new HashMap<>();
        source.foods().forEach((id, food) -> {
            metas.put(
                    id,
                    new FoodMeta(
                            food.getWastePct(), food.getPurchaseUnit(), food.getPurchaseUnitSize(), food.getStorage()));
            // Thực phẩm chưa có mức giá theo ngày thì dùng đơn giá danh mục
            BigDecimal dated = book.price(id, source.date());
            BigDecimal price = dated != null ? dated : food.getUnitPrice();
            if (price != null) {
                prices.put(id, price);
            }
        });
        if (snapshot && source.saved() != null) {
            source.saved().getLines().forEach(l -> {
                Food food = source.foods().get(l.foodId());
                if (food == null) {
                    return;
                }
                metas.put(
                        l.foodId(),
                        new FoodMeta(l.wastePct(), l.purchaseUnit(), l.purchaseUnitSize(), food.getStorage()));
                if (l.unitPrice() != null) {
                    prices.put(l.foodId(), l.unitPrice());
                }
            });
        }
        Draft draft = new Draft(
                counts(source),
                lines,
                metas,
                prices,
                source.saved() == null ? null : source.saved().getRoundingStep());
        // Lần 2 chưa lưu: làm tròn sẵn theo số suất đã chốt, cùng bước với lần 1
        if (seeding && !fromRecipe) {
            BigDecimal step = source.seed().getRoundingStep() == null
                    ? DEFAULT_STEP
                    : source.seed().getRoundingStep();
            draft = draft.withRoundingStep(step)
                    .withLines(DayBalanceCalculator.round(lines, draft.childrenByAgeGroup(), metas, step));
        }
        return draft;
    }

    // Món của từng nhóm tuổi theo thực đơn ngày, kèm định lượng công thức; món chưa khai định lượng không có dòng
    private Map<DishKey, List<Line>> expected(Source source) {
        Map<DishKey, List<Line>> expected = new LinkedHashMap<>();
        for (AgeGroupInfo group : source.groups()) {
            source.items().stream()
                    .filter(item -> item.getMenuBlock() == group.block()
                            && group.sessions().contains(item.getSession()))
                    .sorted(Comparator.comparing(MealPlanItem::getSession))
                    .forEach(item -> {
                        Dish dish = source.dishes().get(item.getDishId());
                        if (dish == null) {
                            return;
                        }
                        List<Line> recipe = dish.getIngredients().stream()
                                .filter(i -> group.id().equals(i.getAgeGroupId()))
                                .sorted(Comparator.comparing(
                                        (DishIngredient i) -> i.getFood().getId()))
                                .map(i -> new Line(
                                        group.id(),
                                        item.getSession(),
                                        dish.getId(),
                                        i.getFood().getId(),
                                        i.getGramsPerChild(),
                                        i.getGramsPerChild()))
                                .toList();
                        if (!recipe.isEmpty()) {
                            expected.put(new DishKey(group.id(), item.getSession(), dish.getId()), recipe);
                        }
                    });
        }
        return expected;
    }

    // Số trẻ: bản đã lưu; ô chưa có thì lần 1 lấy sĩ số báo ăn của lớp năm học hiện hành, lần 2 lấy suất đã chốt
    private List<DayBalanceCount> counts(Source source) {
        Map<String, Integer> saved = new HashMap<>();
        if (source.saved() != null) {
            source.saved().getCounts().forEach(c -> saved.put(c.campusId() + "|" + c.ageGroupId(), c.children()));
        }
        List<DayBalanceCount> counts = new ArrayList<>();
        for (Long campusId : source.campusIds()) {
            Map<Long, Integer> enrolled = source.round() == ROUND_CONFIRMED
                    ? confirmedCounts(campusId, source.date())
                    : demandCalculator.enrolledByAgeGroup(campusId, source.date());
            for (AgeGroupInfo group : source.groups()) {
                int children = saved.getOrDefault(campusId + "|" + group.id(), enrolled.getOrDefault(group.id(), 0));
                counts.add(new DayBalanceCount(campusId, group.id(), children));
            }
        }
        return counts;
    }

    /** Suất đã chốt theo nhóm tuổi của điểm trường–ngày; nhiều bữa thì lấy bữa đông nhất. */
    Map<Long, Integer> confirmedCounts(Long campusId, LocalDate date) {
        Map<Long, Integer> counts = new HashMap<>();
        demandCalculator
                .confirmedServings(mealCountRepository.findByCampusIdAndDate(campusId, date))
                .forEach(s -> counts.merge(s.ageGroupId(), s.portions(), Math::max));
        return counts;
    }

    /** Mọi điểm trường có lớp đã xác nhận đủ sổ báo ăn của ngày. */
    boolean allConfirmed(Source source) {
        return !source.campusIds().isEmpty()
                && source.campusIds().stream().allMatch(campusId -> {
                    List<MealCount> counts = mealCountRepository.findByCampusIdAndDate(campusId, source.date());
                    return !counts.isEmpty() && counts.stream().allMatch(MealCount::isConfirmed);
                });
    }

    /** Số suất đã chốt lại khác số của bản cân đối lần 2 đã lưu. */
    boolean countsChanged(Source source) {
        if (source.saved() == null || source.round() != ROUND_CONFIRMED) {
            return false;
        }
        Map<Long, Map<Long, Integer>> current = new HashMap<>();
        return source.saved().getCounts().stream()
                .anyMatch(c -> current.computeIfAbsent(c.campusId(), id -> confirmedCounts(id, source.date()))
                                .getOrDefault(c.ageGroupId(), 0)
                        != c.children());
    }

    /** Bản lưu không còn đúng: thêm, bớt món sau khi lưu; lần 2 còn tính cả việc chốt lại suất. */
    boolean stale(Source source) {
        if (source.saved() == null) {
            return false;
        }
        if (countsChanged(source)) {
            return true;
        }
        Set<DishKey> saved = source.saved().getLines().stream()
                .map(l -> new DishKey(l.ageGroupId(), l.session(), l.dishId()))
                .collect(Collectors.toSet());
        return !saved.equals(new LinkedHashSet<>(expected(source).keySet()));
    }

    /** Chấm khẩu phần đang xét theo cùng bộ kiểm với kế hoạch tuần, thay công thức món bằng gam của bản cân đối. */
    MealPlanEvaluator.Result evaluate(Source source, Draft draft) {
        Map<String, Long> syntheticIds = new LinkedHashMap<>();
        Map<Long, DishInfo> dishes = new HashMap<>();
        List<ItemPlan> items = new ArrayList<>();
        for (MealPlanItem item : source.items()) {
            Dish dish = source.dishes().get(item.getDishId());
            if (dish == null) {
                continue;
            }
            // Cùng một món ở hai bữa có thể được cân đối khác nhau nên mỗi (khối, bữa, món) là một món riêng
            Long id = syntheticIds.computeIfAbsent(
                    item.getMenuBlock() + "|" + item.getSession() + "|" + item.getDishId(),
                    k -> syntheticIds.size() + 1L);
            dishes.put(id, dishInfo(source, draft, dish, item.getMenuBlock(), item.getSession()));
            items.add(new ItemPlan(item.getSession(), item.getMenuBlock(), id));
        }
        DayPlan day = new DayPlan(source.date(), source.dayOff(), items);
        return MealPlanEvaluator.evaluate(
                List.of(day),
                source.groups(),
                dishes,
                evaluationSupport.normsUntil(source.date()),
                (foodId, date) -> draft.prices().get(foodId));
    }

    private DishInfo dishInfo(Source source, Draft draft, Dish dish, MenuBlock block, MealSession session) {
        Map<Long, List<Portion>> byAgeGroup = new HashMap<>();
        for (AgeGroupInfo group : source.groups()) {
            if (group.block() != block) {
                continue;
            }
            List<Portion> portions = draft.lines().stream()
                    .filter(l -> l.ageGroupId().equals(group.id())
                            && l.session() == session
                            && l.dishId().equals(dish.getId())
                            && source.foods().containsKey(l.foodId()))
                    .map(l -> {
                        FoodMeta meta = draft.foods().get(l.foodId());
                        return new Portion(
                                MealPlanEvaluationSupport.foodInfo(
                                        source.foods().get(l.foodId()), meta.unitSize(), meta.wastePct()),
                                l.grams());
                    })
                    .toList();
            if (!portions.isEmpty()) {
                byAgeGroup.put(group.id(), portions);
            }
        }
        return new DishInfo(dish.getId(), dish.getName(), dish.getDishType(), byAgeGroup);
    }

    /** Ngày đạt: đủ món mọi bữa và mọi nhóm tuổi đạt lượng, chất, tiền. */
    static boolean passed(Source source, MealPlanEvaluator.Result result) {
        return !source.items().isEmpty()
                && !result.evaluations().isEmpty()
                && result.evaluations().stream()
                        .allMatch(e -> OK.equals(e.quantityStatus())
                                && OK.equals(e.qualityStatus())
                                && OK.equals(e.moneyStatus()))
                && result.issues().stream().noneMatch(i -> "NO_DISHES".equals(i.code()));
    }

    /** Lý do chưa đạt của một ngày, mỗi nhóm tuổi một câu. */
    static List<String> problems(MealPlanEvaluator.Result result) {
        List<String> problems = new ArrayList<>(result.issues().stream()
                .filter(i -> "NO_DISHES".equals(i.code()) || "NORM_NOT_SET".equals(i.code()))
                .map(MealPlanResponse.Issue::message)
                .toList());
        for (MealPlanResponse.Evaluation e : result.evaluations()) {
            List<String> failed = new ArrayList<>();
            addFailure(failed, "lượng", e.quantityStatus());
            addFailure(failed, "chất", e.qualityStatus());
            addFailure(failed, "tiền", e.moneyStatus());
            if (!failed.isEmpty()) {
                problems.add(e.ageGroupName() + ": chưa đạt " + String.join(", ", failed));
            }
        }
        return problems;
    }

    private static void addFailure(List<String> failed, String name, String status) {
        // Bữa chưa có món đã báo riêng
        if (!OK.equals(status) && !"NO_DISHES".equals(status)) {
            failed.add(name);
        }
    }

    /** Tên món theo id, để hiển thị dòng. */
    static Map<Long, String> dishNames(Source source) {
        return source.dishes().values().stream().collect(Collectors.toMap(Dish::getId, Dish::getName));
    }

    /** Thực phẩm xuất hiện trong dòng, theo tên. */
    static List<Food> usedFoods(Source source, Draft draft) {
        return draft.lines().stream()
                .map(Line::foodId)
                .distinct()
                .map(id -> source.foods().get(id))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Food::getName).thenComparing(Food::getId))
                .collect(Collectors.toMap(Food::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }
}
