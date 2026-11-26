package com.carenest.service.meals.impl;

import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MealSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tính toán của bản cân đối khẩu phần ngày: sửa gam, làm tròn lượng mua trên tổng toàn trường, chia cho điểm trường.
 * Lượng mua theo đơn vị gốc (g/ml) = Σ nhóm tuổi (số trẻ × gam/trẻ) ÷ (1 − thải bỏ%).
 */
final class DayBalanceCalculator {

    private static final int GRAM_SCALE = 6;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal CENT = new BigDecimal("0.01");
    // Sai số do làm tròn gam 6 chữ số; lệch trong mức này coi là đúng bội của bước mua
    private static final BigDecimal SNAP = new BigDecimal("0.05");

    private DayBalanceCalculator() {}

    /** Gam ăn một trẻ của một thực phẩm trong một món; {@code baseGrams} là số trước khi làm tròn lượng mua. */
    record Line(
            Long ageGroupId, MealSession session, Long dishId, Long foodId, BigDecimal baseGrams, BigDecimal grams) {

        Line withGrams(BigDecimal value) {
            return new Line(ageGroupId, session, dishId, foodId, baseGrams, value);
        }
    }

    record FoodMeta(BigDecimal wastePct, String purchaseUnit, BigDecimal unitSize, FoodStorage storage) {}

    /** Bước mua theo đơn vị gốc: đơn vị đếm mua nguyên đơn vị; cân đo theo bước kg/lít đã chọn. */
    static BigDecimal stepOf(FoodMeta food, BigDecimal measuredStep) {
        if (food == null || measuredStep == null || food.storage() == FoodStorage.DRY) {
            return null;
        }
        if (PurchaseUnits.countable(food.purchaseUnit())
                && food.unitSize() != null
                && food.unitSize().signum() > 0) {
            return food.unitSize();
        }
        return measuredStep.multiply(THOUSAND);
    }

    private static BigDecimal rawPurchase(
            List<Line> lines, Map<Long, Integer> children, Long foodId, FoodMeta food, boolean base) {
        BigDecimal edible = BigDecimal.ZERO;
        for (Line line : lines) {
            if (line.foodId().equals(foodId)) {
                BigDecimal grams = base ? line.baseGrams() : line.grams();
                edible = edible.add(grams.multiply(BigDecimal.valueOf(children.getOrDefault(line.ageGroupId(), 0))));
            }
        }
        return MealDemandCalculator.purchased(edible, food == null ? null : food.wastePct());
    }

    /** Lượng mua toàn trường của một thực phẩm, 2 chữ số; đã làm tròn thì trả đúng bội của bước mua. */
    static BigDecimal purchaseTotal(
            List<Line> lines, Map<Long, Integer> children, Long foodId, FoodMeta food, BigDecimal measuredStep) {
        BigDecimal raw = rawPurchase(lines, children, foodId, food, false);
        BigDecimal step = stepOf(food, measuredStep);
        if (step != null) {
            BigDecimal nearest = raw.divide(step, 0, RoundingMode.HALF_UP).multiply(step);
            if (raw.subtract(nearest).abs().compareTo(SNAP) <= 0) {
                return nearest.setScale(2, RoundingMode.HALF_UP);
            }
        }
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Làm tròn lên lượng mua toàn trường theo từng thực phẩm tươi rồi nhân gam mỗi trẻ cùng một hệ số. Luôn tính từ
     * {@code baseGrams} nên làm tròn nhiều lần cho cùng kết quả; hàng khô giữ nguyên.
     */
    static List<Line> round(
            List<Line> lines, Map<Long, Integer> children, Map<Long, FoodMeta> foods, BigDecimal measuredStep) {
        Map<Long, BigDecimal> factors = new LinkedHashMap<>();
        List<Line> result = new ArrayList<>(lines.size());
        for (Line line : lines) {
            BigDecimal factor = factors.computeIfAbsent(
                    line.foodId(), id -> factorOf(lines, children, id, foods.get(id), measuredStep));
            BigDecimal grams = factor.compareTo(BigDecimal.ONE) == 0
                    ? line.baseGrams()
                    : line.baseGrams().multiply(factor).setScale(GRAM_SCALE, RoundingMode.HALF_UP);
            result.add(line.withGrams(grams));
        }
        return result;
    }

    private static BigDecimal factorOf(
            List<Line> lines, Map<Long, Integer> children, Long foodId, FoodMeta food, BigDecimal measuredStep) {
        BigDecimal step = stepOf(food, measuredStep);
        if (step == null) {
            return BigDecimal.ONE;
        }
        BigDecimal raw = rawPurchase(lines, children, foodId, food, true);
        if (raw.signum() <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal rounded = raw.setScale(2, RoundingMode.HALF_UP)
                .divide(step, 0, RoundingMode.CEILING)
                .multiply(step);
        return rounded.divide(raw, 12, RoundingMode.HALF_UP);
    }

    /** Đặt tổng gam một trẻ của một thực phẩm trong nhóm tuổi, giữ tỷ lệ giữa các món (đang 0 thì chia đều). */
    static List<Line> setGrams(List<Line> lines, Long ageGroupId, Long foodId, BigDecimal gramsPerChild) {
        List<Line> matching = lines.stream()
                .filter(l -> l.ageGroupId().equals(ageGroupId) && l.foodId().equals(foodId))
                .toList();
        if (matching.isEmpty()) {
            return lines;
        }
        BigDecimal current = matching.stream().map(Line::grams).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Line> result = new ArrayList<>(lines.size());
        for (Line line : lines) {
            if (!line.ageGroupId().equals(ageGroupId) || !line.foodId().equals(foodId)) {
                result.add(line);
                continue;
            }
            BigDecimal share = current.signum() == 0
                    ? gramsPerChild.divide(BigDecimal.valueOf(matching.size()), GRAM_SCALE, RoundingMode.HALF_UP)
                    : gramsPerChild.multiply(line.grams()).divide(current, GRAM_SCALE, RoundingMode.HALF_UP);
            result.add(new Line(ageGroupId, line.session(), line.dishId(), foodId, share, share));
        }
        return result;
    }

    /** Gam ăn một trẻ ứng với lượng mua (đơn vị gốc) cho {@code children} trẻ. */
    static BigDecimal gramsForPurchase(BigDecimal purchase, int children, BigDecimal wastePct) {
        BigDecimal waste = wastePct == null || wastePct.signum() <= 0 || wastePct.compareTo(HUNDRED) >= 0
                ? BigDecimal.ZERO
                : wastePct;
        return purchase.multiply(HUNDRED.subtract(waste))
                .divide(HUNDRED.multiply(BigDecimal.valueOf(children)), GRAM_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Chia {@code total} theo trọng số bằng phần dư lớn nhất: mỗi phần là bội của {@code step} khi tổng chia hết cho
     * bước, nếu không thì chia tới 0,01. Các phần cộng lại đúng {@code total}.
     */
    static Map<Long, BigDecimal> allocate(BigDecimal total, Map<Long, BigDecimal> weights, BigDecimal step) {
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        weights.keySet().forEach(key -> shares.put(key, BigDecimal.ZERO));
        BigDecimal sum = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() <= 0 || total.signum() <= 0) {
            return shares;
        }
        BigDecimal unit = step != null && total.remainder(step).signum() == 0 ? step : CENT;
        long units = total.divide(unit, 0, RoundingMode.HALF_UP).longValue();
        record Rest(Long key, BigDecimal fraction) {}
        List<Rest> rests = new ArrayList<>();
        long given = 0;
        for (Map.Entry<Long, BigDecimal> weight : weights.entrySet()) {
            BigDecimal exact =
                    BigDecimal.valueOf(units).multiply(weight.getValue()).divide(sum, 10, RoundingMode.HALF_UP);
            BigDecimal whole = exact.setScale(0, RoundingMode.FLOOR);
            given += whole.longValue();
            shares.put(weight.getKey(), whole);
            rests.add(new Rest(weight.getKey(), exact.subtract(whole)));
        }
        rests.sort(Comparator.comparing(Rest::fraction).reversed().thenComparing(Rest::key));
        for (int i = 0; i < units - given; i++) {
            Long key = rests.get(i % rests.size()).key();
            shares.put(key, shares.get(key).add(BigDecimal.ONE));
        }
        shares.replaceAll((key, count) -> count.multiply(unit));
        return shares;
    }
}
