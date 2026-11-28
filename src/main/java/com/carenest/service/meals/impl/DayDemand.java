package com.carenest.service.meals.impl;

import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.service.meals.impl.DayBalanceCalculator.FoodMeta;
import com.carenest.service.meals.impl.DayBalanceCalculator.Line;
import com.carenest.utils.enums.FoodStorage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Nguồn nhu cầu thực phẩm của một ngày cho dự trù, đi chợ, kiểm nhận, trừ kho và bếp: ngày đã cân đối thì theo bản cân
 * đối (lần 2 nếu có, không thì lần 1), chưa cân đối thì theo công thức món như {@link MealDemandCalculator}.
 */
@Component
@RequiredArgsConstructor
class DayDemand {

    private final DayBalanceReader reader;
    private final MealDemandCalculator calculator;

    /** Bản cân đối đã lưu đang áp dụng cho một ngày. */
    record Balanced(DayBalanceReader.Source source, DayBalanceReader.Draft draft, int round) {}

    /** Bản mới nhất đã lưu của ngày (lần 2 trước, rồi lần 1); chưa cân đối ⇒ rỗng. */
    Optional<Balanced> latest(LocalDate date) {
        Optional<Balanced> confirmed = of(date, DayBalanceReader.ROUND_CONFIRMED);
        return confirmed.isPresent() ? confirmed : of(date, DayBalanceReader.ROUND_EXPECTED);
    }

    Optional<Balanced> of(LocalDate date, short round) {
        if (!reader.saved(date, round)) {
            return Optional.empty();
        }
        DayBalanceReader.Source source = reader.load(date, round);
        return Optional.of(new Balanced(source, reader.compose(source, true), round));
    }

    /** foodId → lượng cần mua (đơn vị gốc) cho các suất đã cho: gam mỗi trẻ của bản cân đối × suất, chưa làm tròn. */
    Map<Long, BigDecimal> byServings(
            MealPlanDay day, Collection<MealDemandCalculator.Servings> servings, FoodStorage storage) {
        if (servings.isEmpty()) {
            return Map.of();
        }
        Optional<Balanced> balanced = latest(day.getMenuDate());
        if (balanced.isEmpty()) {
            return calculator.demand(day, servings, storage);
        }
        return byServings(balanced.get(), servings, storage);
    }

    Map<Long, BigDecimal> byServings(
            Balanced balanced, Collection<MealDemandCalculator.Servings> servings, FoodStorage storage) {
        Map<Long, BigDecimal> edible = new HashMap<>();
        for (Line line : balanced.draft().lines()) {
            if (!isStored(balanced, line.foodId(), storage)) {
                continue;
            }
            for (MealDemandCalculator.Servings serving : servings) {
                if (serving.session() == line.session()
                        && serving.ageGroupId().equals(line.ageGroupId())
                        && serving.portions() > 0) {
                    edible.merge(
                            line.foodId(),
                            line.grams().multiply(BigDecimal.valueOf(serving.portions())),
                            BigDecimal::add);
                }
            }
        }
        Map<Long, BigDecimal> purchase = new HashMap<>();
        edible.forEach((foodId, quantity) -> purchase.put(
                foodId,
                MealDemandCalculator.purchased(
                                quantity, balanced.draft().foods().get(foodId).wastePct())
                        .setScale(2, RoundingMode.HALF_UP)));
        return purchase;
    }

    /** Phần của một điểm trường trong lượng mua đã cân đối (đã làm tròn trên tổng toàn trường); bỏ lượng 0. */
    Map<Long, BigDecimal> campusShare(Balanced balanced, Long campusId, FoodStorage storage) {
        Map<Long, BigDecimal> share = new LinkedHashMap<>();
        Map<Long, Integer> children = balanced.draft().childrenByAgeGroup();
        balanced.draft().lines().stream()
                .map(Line::foodId)
                .distinct()
                .sorted()
                .filter(foodId -> isStored(balanced, foodId, storage))
                .forEach(foodId -> {
                    FoodMeta meta = balanced.draft().foods().get(foodId);
                    BigDecimal total = DayBalanceCalculator.purchaseTotal(
                            balanced.draft().lines(),
                            children,
                            foodId,
                            meta,
                            balanced.draft().roundingStep());
                    BigDecimal quantity = DayBalanceReader.campusShares(
                                    balanced.source(), balanced.draft(), foodId, total)
                            .getOrDefault(campusId, BigDecimal.ZERO);
                    if (quantity.signum() > 0) {
                        share.put(foodId, quantity.setScale(2, RoundingMode.HALF_UP));
                    }
                });
        return share;
    }

    private static boolean isStored(Balanced balanced, Long foodId, FoodStorage storage) {
        Food food = balanced.source().foods().get(foodId);
        return food != null && food.getStorage() == storage;
    }
}
