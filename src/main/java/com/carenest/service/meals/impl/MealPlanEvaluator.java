package com.carenest.service.meals.impl;

import com.carenest.dto.meals.MealPlanResponse.Evaluation;
import com.carenest.dto.meals.MealPlanResponse.Issue;
import com.carenest.dto.meals.MealPlanResponse.NormRow;
import com.carenest.dto.meals.MealPlanResponse.NutritionTotals;
import com.carenest.dto.meals.MealPlanResponse.SessionRow;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Kiểm tự động kế hoạch tuần (NUT-06, NUT-08, NUT-09, NUT-19, NUT-20, NUT-24, NUT-25): tính xác định, cùng công thức
 * {@code CareNest_FE:src/utils/menu-planning/menuCalculations.js} (làm tròn 0,1 theo món, bữa, ngày) để màn lập và
 * BE chặn công bố cho cùng kết quả.
 */
final class MealPlanEvaluator {

    static final String BLOCKING = "BLOCKING";
    static final String WARNING = "WARNING";
    static final String INFO = "INFO";

    private static final String OK = "OK";
    private static final String LOW = "LOW";
    private static final String HIGH = "HIGH";
    private static final String NA = "NA";
    private static final String INCOMPLETE = "INCOMPLETE";
    private static final String NO_DISHES = "NO_DISHES";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final Map<String, BigDecimal> ENERGY_FACTOR =
            Map.of("protein", BigDecimal.valueOf(4), "lipid", BigDecimal.valueOf(9), "glucid", BigDecimal.valueOf(4));
    private static final Map<String, String> ROW_LABELS = Map.of(
            "kcal", "Năng lượng",
            "protein", "Tỷ lệ năng lượng từ đạm",
            "lipid", "Tỷ lệ năng lượng từ béo",
            "glucid", "Tỷ lệ năng lượng từ bột đường",
            "animalProteinG", "Đạm động vật",
            "plantProteinG", "Đạm thực vật",
            "animalLipidG", "Béo động vật",
            "plantLipidG", "Béo thực vật",
            "glucidG", "Bột đường");
    private static final Map<MealSession, String> SESSION_LABELS =
            Map.of(MealSession.MORNING, "sáng", MealSession.LUNCH, "trưa", MealSession.AFTERNOON, "chiều");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    record AgeGroupInfo(Long id, String name, MenuBlock block, List<MealSession> sessions) {}

    record FoodInfo(
            Long id,
            BigDecimal kcal,
            BigDecimal protein,
            BigDecimal lipid,
            BigDecimal glucid,
            FoodOrigin origin,
            BigDecimal unitPrice,
            BigDecimal purchaseUnitSize,
            BigDecimal wastePct,
            List<String> allergens) {}

    record Portion(FoodInfo food, BigDecimal grams) {}

    /** Món: định lượng theo nhóm tuổi (NUT-18). */
    record DishInfo(Long id, String name, DishType type, Map<Long, List<Portion>> byAgeGroup) {}

    /** Khoảng định mức; một đầu chưa nhập thì coi như chưa đặt. */
    record Range(BigDecimal min, BigDecimal max) {
        static final Range NONE = new Range(null, null);

        boolean set() {
            return min != null && max != null;
        }
    }

    /**
     * Định mức hiệu lực của nhóm tuổi tại ngày (NUT-17): kcal; cơ cấu P:L:G theo % năng lượng; đạm, béo ĐV-TV và bột
     * đường theo gam/trẻ/ngày (NUT-24); % năng lượng từng bữa so nhu cầu năng lượng cả ngày {@code dailyKcalNeed} (NUT-25).
     */
    record Norm(
            BigDecimal mealPrice,
            Range kcal,
            Range proteinPct,
            Range lipidPct,
            Range glucidPct,
            Range animalProteinG,
            Range plantProteinG,
            Range animalLipidG,
            Range plantLipidG,
            Range glucidG,
            Map<MealSession, Range> sessionKcalPct,
            BigDecimal dailyKcalNeed) {}

    record ItemPlan(MealSession session, MenuBlock block, Long dishId) {}

    record DayPlan(LocalDate date, boolean off, List<ItemPlan> items) {}

    record Result(List<Evaluation> evaluations, List<Issue> issues) {
        boolean publishable() {
            return issues.stream().noneMatch(i -> BLOCKING.equals(i.severity()));
        }
    }

    /** Bộ đếm 8 chỉ số; thứ tự như FE: kcal, protein, lipid, glucid, đạm/béo động vật/thực vật. */
    private static final class Acc {
        BigDecimal kcal = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal lipid = BigDecimal.ZERO;
        BigDecimal glucid = BigDecimal.ZERO;
        BigDecimal animalProtein = BigDecimal.ZERO;
        BigDecimal plantProtein = BigDecimal.ZERO;
        BigDecimal animalLipid = BigDecimal.ZERO;
        BigDecimal plantLipid = BigDecimal.ZERO;

        void add(Acc other) {
            kcal = kcal.add(other.kcal);
            protein = protein.add(other.protein);
            lipid = lipid.add(other.lipid);
            glucid = glucid.add(other.glucid);
            animalProtein = animalProtein.add(other.animalProtein);
            plantProtein = plantProtein.add(other.plantProtein);
            animalLipid = animalLipid.add(other.animalLipid);
            plantLipid = plantLipid.add(other.plantLipid);
        }

        Acc rounded() {
            Acc r = new Acc();
            r.kcal = round1(kcal);
            r.protein = round1(protein);
            r.lipid = round1(lipid);
            r.glucid = round1(glucid);
            r.animalProtein = round1(animalProtein);
            r.plantProtein = round1(plantProtein);
            r.animalLipid = round1(animalLipid);
            r.plantLipid = round1(plantLipid);
            return r;
        }

        NutritionTotals toTotals() {
            return new NutritionTotals(
                    kcal, protein, lipid, glucid, animalProtein, plantProtein, animalLipid, plantLipid);
        }
    }

    private MealPlanEvaluator() {}

    static Result evaluate(
            List<DayPlan> days,
            List<AgeGroupInfo> groups,
            Map<Long, DishInfo> dishes,
            BiFunction<Long, LocalDate, Norm> normOf) {
        List<Evaluation> evaluations = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        Map<Long, LocalDate> normMissing = new LinkedHashMap<>();
        for (DayPlan day : days) {
            if (day.off()) {
                continue;
            }
            missingSessions(day, groups, issues);
            for (AgeGroupInfo group : groups) {
                Evaluation evaluation = evaluateGroup(day, group, dishes, normOf.apply(group.id(), day.date()));
                evaluations.add(evaluation);
                groupIssues(day.date(), group, evaluation, issues, normMissing);
            }
            allergens(day, dishes, issues);
        }
        normMissing.forEach((groupId, date) -> issues.add(new Issue(
                BLOCKING,
                "NORM_NOT_SET",
                date,
                groupId,
                null,
                null,
                nameOf(groups, groupId) + ": chưa đặt định mức năng lượng và cơ cấu đạm, béo, bột đường"
                        + " (Danh mục & thiết lập › Cấu hình nhóm tuổi)")));
        repeatedMainDishes(days, dishes, issues);
        issues.sort(Comparator.comparingInt((Issue i) -> severityOrder(i.severity()))
                .thenComparing(Issue::date, Comparator.nullsFirst(Comparator.naturalOrder())));
        return new Result(evaluations, issues);
    }

    // NUT-08: mỗi bữa khối phục vụ phải có món
    private static void missingSessions(DayPlan day, List<AgeGroupInfo> groups, List<Issue> issues) {
        Map<MenuBlock, Set<MealSession>> served = new EnumMap<>(MenuBlock.class);
        groups.forEach(g ->
                served.computeIfAbsent(g.block(), b -> new LinkedHashSet<>()).addAll(g.sessions()));
        served.forEach((block, sessions) -> sessions.stream().sorted().forEach(session -> {
            boolean hasDish = day.items().stream().anyMatch(i -> i.block() == block && i.session() == session);
            if (!hasDish) {
                issues.add(new Issue(
                        BLOCKING,
                        NO_DISHES,
                        day.date(),
                        null,
                        block,
                        null,
                        day.date().format(DAY) + " · " + blockLabel(block) + ": bữa " + SESSION_LABELS.get(session)
                                + " chưa có món"));
            }
        }));
    }

    private static Evaluation evaluateGroup(DayPlan day, AgeGroupInfo group, Map<Long, DishInfo> dishes, Norm norm) {
        List<ItemPlan> items = day.items().stream()
                .filter(i -> i.block() == group.block() && group.sessions().contains(i.session()))
                .toList();
        BigDecimal mealPrice = norm == null ? null : norm.mealPrice();
        if (items.isEmpty()) {
            return new Evaluation(
                    day.date(),
                    group.id(),
                    group.name(),
                    group.block(),
                    NO_DISHES,
                    new Acc().toTotals(),
                    BigDecimal.ZERO,
                    mealPrice,
                    List.of(),
                    List.of());
        }
        Acc day0 = new Acc();
        Map<MealSession, BigDecimal> sessionKcal = new EnumMap<>(MealSession.class);
        List<String> missing = new ArrayList<>();
        long cost = 0;
        for (MealSession session : MealSession.values()) {
            Acc sessionAcc = new Acc();
            for (ItemPlan item : items) {
                if (item.session() != session) {
                    continue;
                }
                DishInfo dish = dishes.get(item.dishId());
                if (dish == null) {
                    addOnce(missing, "món đã xóa");
                    continue;
                }
                List<Portion> portions = dish.byAgeGroup().getOrDefault(group.id(), List.of());
                if (portions.isEmpty()) {
                    addOnce(missing, "định lượng món " + dish.name());
                    continue;
                }
                sessionAcc.add(dishTotals(portions));
                cost += dishCost(portions);
            }
            Acc sessionRounded = sessionAcc.rounded();
            day0.add(sessionRounded);
            if (group.sessions().contains(session)) {
                sessionKcal.put(session, sessionRounded.kcal);
            }
        }
        Acc totals = day0.rounded();
        List<NormRow> rows = compareToNorm(totals, norm, !missing.isEmpty());
        List<SessionRow> sessions = compareSessions(sessionKcal, totals.kcal, norm, !missing.isEmpty());
        String status = overall(rows);
        return new Evaluation(
                day.date(),
                group.id(),
                group.name(),
                group.block(),
                status,
                totals.toTotals(),
                BigDecimal.valueOf(cost),
                mealPrice,
                rows,
                sessions);
    }

    /** Một món cho một trẻ: dinh dưỡng trên 100 g/ml ăn được × gam/100, làm tròn 0,1 (FE {@code dishTotals}). */
    private static Acc dishTotals(List<Portion> portions) {
        Acc acc = new Acc();
        for (Portion p : portions) {
            FoodInfo f = p.food();
            BigDecimal ratio = p.grams().divide(HUNDRED, 10, RoundingMode.HALF_UP);
            acc.kcal = acc.kcal.add(f.kcal().multiply(ratio));
            acc.glucid = acc.glucid.add(f.glucid().multiply(ratio));
            BigDecimal protein = f.protein().multiply(ratio);
            BigDecimal lipid = f.lipid().multiply(ratio);
            if (f.origin() == FoodOrigin.ANIMAL) {
                acc.animalProtein = acc.animalProtein.add(protein);
                acc.animalLipid = acc.animalLipid.add(lipid);
            } else {
                acc.plantProtein = acc.plantProtein.add(protein);
                acc.plantLipid = acc.plantLipid.add(lipid);
            }
        }
        Acc r = acc.rounded();
        r.protein = round1(r.animalProtein.add(r.plantProtein));
        r.lipid = round1(r.animalLipid.add(r.plantLipid));
        return r;
    }

    // Giá/trẻ = giá đơn vị mua ÷ cỡ đơn vị mua × lượng mua (gam ÷ (1 − thải bỏ%)), làm tròn đồng theo món (FE
    // dishTotals)
    private static long dishCost(List<Portion> portions) {
        BigDecimal cost = BigDecimal.ZERO;
        for (Portion p : portions) {
            FoodInfo f = p.food();
            if (f.unitPrice() != null
                    && f.purchaseUnitSize() != null
                    && f.purchaseUnitSize().signum() > 0) {
                cost = cost.add(f.unitPrice()
                        .multiply(MealDemandCalculator.purchased(p.grams(), f.wastePct()))
                        .divide(f.purchaseUnitSize(), 6, RoundingMode.HALF_UP));
            }
        }
        return cost.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * 4 dòng chính (kcal + cơ cấu P:L:G) cần đủ 4 khoảng mới đánh giá được; 5 dòng gam (đạm, béo ĐV-TV, bột đường) chỉ
     * đánh giá khi đã nhập khoảng đó (NUT-24).
     */
    private static List<NormRow> compareToNorm(Acc totals, Norm norm, boolean incomplete) {
        boolean core = norm != null
                && norm.kcal().set()
                && norm.proteinPct().set()
                && norm.lipidPct().set()
                && norm.glucidPct().set();
        BigDecimal kcal = totals.kcal;
        Range kcalRange = core ? norm.kcal() : Range.NONE;
        BigDecimal kcalTarget = core ? mid(kcalRange.min(), kcalRange.max()) : null;
        List<NormRow> rows = new ArrayList<>();
        rows.add(new NormRow(
                "kcal",
                kcal,
                null,
                kcalRange.min(),
                kcalRange.max(),
                kcalTarget,
                kcalTarget == null || kcalTarget.signum() == 0 ? null : percentInt(kcal, kcalTarget),
                status(core, incomplete, kcal, kcal, kcalRange.min(), kcalRange.max()),
                false));
        rows.add(energyRow("protein", totals.protein, kcal, kcalTarget, incomplete, core ? norm.proteinPct() : null));
        rows.add(energyRow("lipid", totals.lipid, kcal, kcalTarget, incomplete, core ? norm.lipidPct() : null));
        rows.add(energyRow("glucid", totals.glucid, kcal, kcalTarget, incomplete, core ? norm.glucidPct() : null));
        rows.add(gramRow(
                "animalProteinG",
                totals.animalProtein,
                share(totals.animalProtein, totals.protein),
                kcal,
                incomplete,
                norm == null ? Range.NONE : norm.animalProteinG()));
        rows.add(gramRow(
                "plantProteinG",
                totals.plantProtein,
                share(totals.plantProtein, totals.protein),
                kcal,
                incomplete,
                norm == null ? Range.NONE : norm.plantProteinG()));
        rows.add(gramRow(
                "animalLipidG",
                totals.animalLipid,
                share(totals.animalLipid, totals.lipid),
                kcal,
                incomplete,
                norm == null ? Range.NONE : norm.animalLipidG()));
        rows.add(gramRow(
                "plantLipidG",
                totals.plantLipid,
                share(totals.plantLipid, totals.lipid),
                kcal,
                incomplete,
                norm == null ? Range.NONE : norm.plantLipidG()));
        rows.add(gramRow("glucidG", totals.glucid, null, kcal, incomplete, norm == null ? Range.NONE : norm.glucidG()));
        return rows;
    }

    /** Dòng cơ cấu P:L:G: {@code percent} = % năng lượng; {@code pct} null khi chưa đủ 4 khoảng chính. */
    private static NormRow energyRow(
            String key, BigDecimal value, BigDecimal kcal, BigDecimal kcalTarget, boolean incomplete, Range pct) {
        boolean core = pct != null;
        BigDecimal min = core ? pct.min() : null;
        BigDecimal max = core ? pct.max() : null;
        BigDecimal factor = ENERGY_FACTOR.get(key);
        BigDecimal percent = kcal.signum() == 0
                ? null
                : round1(factor.multiply(value).multiply(HUNDRED).divide(kcal, 10, RoundingMode.HALF_UP));
        BigDecimal target = core
                ? round1(kcalTarget.multiply(mid(min, max)).divide(HUNDRED.multiply(factor), 10, RoundingMode.HALF_UP))
                : null;
        Integer attainment = target == null || target.signum() == 0 ? null : percentInt(value, target);
        return new NormRow(
                key,
                value,
                percent,
                min,
                max,
                target,
                attainment,
                status(core, incomplete, percent, kcal, min, max),
                false);
    }

    /** Dòng gam: {@code percent} = % ĐV hoặc TV trên tổng đạm/béo (tỷ lệ cơ cấu), {@code attainment} so giữa khoảng. */
    private static NormRow gramRow(
            String key, BigDecimal value, BigDecimal share, BigDecimal kcal, boolean incomplete, Range range) {
        boolean configured = range.set();
        BigDecimal target = configured ? round1(mid(range.min(), range.max())) : null;
        return new NormRow(
                key,
                value,
                share,
                configured ? range.min() : null,
                configured ? range.max() : null,
                target,
                target == null || target.signum() == 0 ? null : percentInt(value, target),
                status(configured, incomplete, value, kcal, range.min(), range.max()),
                true);
    }

    // NUT-25: kcal bữa ÷ nhu cầu năng lượng cả ngày của nhóm tuổi (chưa đặt nhu cầu ⇒ NA); chỉ cảnh báo, không tính vào
    // trạng thái ngày
    private static List<SessionRow> compareSessions(
            Map<MealSession, BigDecimal> sessionKcal, BigDecimal dayKcal, Norm norm, boolean incomplete) {
        List<SessionRow> rows = new ArrayList<>();
        BigDecimal need = norm == null ? null : norm.dailyKcalNeed();
        sessionKcal.forEach((session, kcal) -> {
            Range range = norm == null ? Range.NONE : norm.sessionKcalPct().getOrDefault(session, Range.NONE);
            BigDecimal percent = need == null ? null : share(kcal, need);
            boolean configured = range.set() && need != null;
            rows.add(new SessionRow(
                    session,
                    kcal,
                    percent,
                    range.set() ? range.min() : null,
                    range.set() ? range.max() : null,
                    status(configured, incomplete, percent, dayKcal, range.min(), range.max())));
        });
        return rows;
    }

    private static BigDecimal share(BigDecimal part, BigDecimal total) {
        return total.signum() == 0 ? null : round1(part.multiply(HUNDRED).divide(total, 10, RoundingMode.HALF_UP));
    }

    private static String status(
            boolean configured,
            boolean incomplete,
            BigDecimal measured,
            BigDecimal kcal,
            BigDecimal min,
            BigDecimal max) {
        if (!configured) {
            return NA;
        }
        if (incomplete || measured == null || kcal.signum() == 0) {
            return INCOMPLETE;
        }
        if (measured.compareTo(min) < 0) {
            return LOW;
        }
        return measured.compareTo(max) > 0 ? HIGH : OK;
    }

    /** FE {@code overallNormStatus}: thiếu dữ liệu > chưa đặt định mức > thấp/cao > đạt; dòng tùy chọn NA bỏ qua. */
    private static String overall(List<NormRow> rows) {
        List<NormRow> counted = rows.stream()
                .filter(r -> !(r.optional() && NA.equals(r.status())))
                .toList();
        if (counted.stream().anyMatch(r -> INCOMPLETE.equals(r.status()))) {
            return INCOMPLETE;
        }
        if (counted.stream().anyMatch(r -> NA.equals(r.status()))) {
            return NA;
        }
        return counted.stream()
                .map(NormRow::status)
                .filter(s -> LOW.equals(s) || HIGH.equals(s))
                .findFirst()
                .orElse(OK);
    }

    private static void groupIssues(
            LocalDate date,
            AgeGroupInfo group,
            Evaluation evaluation,
            List<Issue> issues,
            Map<Long, LocalDate> normMissing) {
        String prefix = date.format(DAY) + " · " + group.name() + ": ";
        switch (evaluation.status()) {
            case LOW, HIGH ->
                issues.add(new Issue(
                        BLOCKING,
                        "NOT_BALANCED",
                        date,
                        group.id(),
                        group.block(),
                        null,
                        prefix
                                + evaluation.rows().stream()
                                        .filter(r -> LOW.equals(r.status()) || HIGH.equals(r.status()))
                                        .map(MealPlanEvaluator::describe)
                                        .collect(Collectors.joining("; "))));
            case INCOMPLETE ->
                issues.add(new Issue(
                        BLOCKING,
                        "INCOMPLETE_DATA",
                        date,
                        group.id(),
                        group.block(),
                        null,
                        prefix + "chưa đủ số liệu để kiểm cân đối (thiếu định lượng món cho nhóm tuổi)"));
            case NA -> normMissing.putIfAbsent(group.id(), date);
            default -> {
                // NO_DISHES đã báo theo bữa của khối; OK không có lỗi
            }
        }
        // NUT-19: vượt giá suất chỉ cảnh báo
        if (evaluation.mealPrice() != null && evaluation.cost().compareTo(evaluation.mealPrice()) > 0) {
            issues.add(new Issue(
                    WARNING,
                    "OVER_BUDGET",
                    date,
                    group.id(),
                    group.block(),
                    null,
                    prefix + "chi phí " + plain(evaluation.cost()) + "đ/trẻ vượt giá suất "
                            + plain(evaluation.mealPrice()) + "đ"));
        }
        List<SessionRow> offSessions = evaluation.sessions().stream()
                .filter(r -> LOW.equals(r.status()) || HIGH.equals(r.status()))
                .toList();
        if (!offSessions.isEmpty()) {
            issues.add(new Issue(
                    WARNING,
                    "SESSION_KCAL_OFF",
                    date,
                    group.id(),
                    group.block(),
                    null,
                    prefix
                            + offSessions.stream()
                                    .map(r -> "bữa " + SESSION_LABELS.get(r.session()) + " " + plain(r.percent())
                                            + "% nhu cầu năng lượng cả ngày (định mức " + plain(r.min()) + "–"
                                            + plain(r.max()) + "%)")
                                    .collect(Collectors.joining("; "))));
        }
    }

    private static String describe(NormRow row) {
        String direction = LOW.equals(row.status()) ? "thấp hơn" : "cao hơn";
        boolean energyShare = ENERGY_FACTOR.containsKey(row.key());
        BigDecimal measured = energyShare ? row.percent() : row.value();
        String unit = energyShare ? "%" : "kcal".equals(row.key()) ? " kcal" : " g";
        return ROW_LABELS.get(row.key()) + " " + direction + " định mức (" + plain(measured) + unit + " so với "
                + plain(row.min()) + "–" + plain(row.max()) + unit + ")";
    }

    // NUT-20: món chính lặp trong tuần cùng khối — cảnh báo, không chặn
    private static void repeatedMainDishes(List<DayPlan> days, Map<Long, DishInfo> dishes, List<Issue> issues) {
        Map<MenuBlock, Map<Long, List<LocalDate>>> seen = new EnumMap<>(MenuBlock.class);
        for (DayPlan day : days) {
            if (day.off()) {
                continue;
            }
            day.items().stream()
                    .filter(i -> dishes.containsKey(i.dishId())
                            && dishes.get(i.dishId()).type() == DishType.MAIN)
                    .forEach(i -> {
                        List<LocalDate> dates = seen.computeIfAbsent(i.block(), b -> new LinkedHashMap<>())
                                .computeIfAbsent(i.dishId(), d -> new ArrayList<>());
                        if (!dates.contains(day.date())) {
                            dates.add(day.date());
                        }
                    });
        }
        seen.forEach((block, byDish) -> byDish.forEach((dishId, dates) -> {
            if (dates.size() > 1) {
                issues.add(new Issue(
                        WARNING,
                        "DISH_REPEATED",
                        dates.get(1),
                        null,
                        block,
                        dishId,
                        blockLabel(block) + ": món chính " + dishes.get(dishId).name() + " lặp " + dates.size()
                                + " ngày trong tuần ("
                                + dates.stream().map(d -> d.format(DAY)).collect(Collectors.joining(", "))
                                + ")"));
            }
        }));
    }

    // NUT-09: chất gây dị ứng có trong ngày (số trẻ dị ứng theo chất chưa có ở BE) — chỉ thông tin
    private static void allergens(DayPlan day, Map<Long, DishInfo> dishes, List<Issue> issues) {
        Map<MenuBlock, Set<String>> byBlock = new EnumMap<>(MenuBlock.class);
        day.items().forEach(item -> {
            DishInfo dish = dishes.get(item.dishId());
            if (dish == null) {
                return;
            }
            Set<String> names = byBlock.computeIfAbsent(item.block(), b -> new LinkedHashSet<>());
            dish.byAgeGroup()
                    .values()
                    .forEach(portions ->
                            portions.forEach(p -> names.addAll(p.food().allergens())));
        });
        byBlock.forEach((block, names) -> {
            if (!names.isEmpty()) {
                issues.add(new Issue(
                        INFO,
                        "ALLERGENS",
                        day.date(),
                        null,
                        block,
                        null,
                        day.date().format(DAY) + " · " + blockLabel(block) + ": có " + String.join(", ", names)));
            }
        });
    }

    private static boolean range(BigDecimal min, BigDecimal max) {
        return min != null && max != null;
    }

    private static BigDecimal mid(BigDecimal min, BigDecimal max) {
        return min.add(max).divide(TWO, 10, RoundingMode.HALF_UP);
    }

    private static Integer percentInt(BigDecimal value, BigDecimal target) {
        return value.multiply(HUNDRED).divide(target, 0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal round1(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }

    private static void addOnce(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private static String blockLabel(MenuBlock block) {
        return block == MenuBlock.NURSERY ? "Nhà trẻ" : "Mẫu giáo";
    }

    private static String nameOf(List<AgeGroupInfo> groups, Long groupId) {
        return groups.stream()
                .filter(g -> g.id().equals(groupId))
                .map(AgeGroupInfo::name)
                .findFirst()
                .orElse("Nhóm tuổi");
    }

    private static int severityOrder(String severity) {
        return switch (severity) {
            case BLOCKING -> 0;
            case WARNING -> 1;
            default -> 2;
        };
    }
}
