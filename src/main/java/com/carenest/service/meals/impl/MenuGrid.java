package com.carenest.service.meals.impl;

import com.carenest.service.meals.impl.MealPlanEvaluator.DayPlan;
import com.carenest.service.meals.impl.MealPlanEvaluator.ItemPlan;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Lưới món của một phương án: ngày phục vụ → khóa ô → món, kèm lý do theo ngày (chỉ phương án AI có, đã làm sạch).
 * Bất biến: sửa ô trả lưới mới để giữ nguyên phương án trước khi so điểm.
 */
record MenuGrid(Map<LocalDate, Map<String, Long>> cells, Map<LocalDate, String> reasons) {

    MenuGrid {
        Map<LocalDate, Map<String, Long>> copy = new TreeMap<>();
        cells.forEach((date, bySlot) -> copy.put(date, Collections.unmodifiableMap(new LinkedHashMap<>(bySlot))));
        cells = Collections.unmodifiableMap(copy);
        reasons = Collections.unmodifiableMap(new TreeMap<>(reasons));
    }

    static MenuGrid empty() {
        return new MenuGrid(Map.of(), Map.of());
    }

    Long dish(LocalDate date, String slotKey) {
        return cells.getOrDefault(date, Map.of()).get(slotKey);
    }

    MenuGrid with(LocalDate date, String slotKey, Long dishId) {
        Map<LocalDate, Map<String, Long>> next = new TreeMap<>(cells);
        Map<String, Long> day = new LinkedHashMap<>(next.getOrDefault(date, Map.of()));
        day.put(slotKey, dishId);
        next.put(date, day);
        return new MenuGrid(next, reasons);
    }

    /** Thay các ngày có trong {@code days}; ngày khác giữ nguyên (sửa một phần theo vòng sửa của model). */
    MenuGrid merge(MenuGrid days) {
        Map<LocalDate, Map<String, Long>> next = new TreeMap<>(cells);
        next.putAll(days.cells());
        Map<LocalDate, String> nextReasons = new TreeMap<>(reasons);
        days.cells().keySet().forEach(nextReasons::remove);
        nextReasons.putAll(days.reasons());
        return new MenuGrid(next, nextReasons);
    }

    /** Món đã có ở ô khác cùng bữa trong ngày (một món chỉ một lần mỗi bữa — khóa duy nhất của kế hoạch tuần). */
    boolean usedInSession(LocalDate date, MenuSlot slot, Long dishId, List<MenuSlot> slots) {
        Map<String, Long> day = cells.getOrDefault(date, Map.of());
        return slots.stream()
                .filter(s -> s.session() == slot.session() && !s.key().equals(slot.key()))
                .anyMatch(s -> Objects.equals(day.get(s.key()), dishId));
    }

    /** Lưới theo dạng input của {@link MealPlanEvaluator}: đủ 5 ngày, ngày nghỉ đánh dấu off. */
    List<DayPlan> toDayPlans(MenuContext ctx) {
        List<DayPlan> days = new ArrayList<>();
        for (LocalDate date : ctx.dates()) {
            days.add(dayPlan(date, ctx));
        }
        return days;
    }

    DayPlan dayPlan(LocalDate date, MenuContext ctx) {
        if (ctx.offDays().contains(date)) {
            return new DayPlan(date, true, List.of());
        }
        Map<String, Long> day = cells.getOrDefault(date, Map.of());
        List<ItemPlan> items = new ArrayList<>();
        for (MenuSlot slot : ctx.slots()) {
            Long dishId = day.get(slot.key());
            if (dishId != null) {
                items.add(new ItemPlan(slot.session(), ctx.block(), dishId));
            }
        }
        return new DayPlan(date, false, items.stream().distinct().toList());
    }

    MealPlanEvaluator.Result evaluate(MenuContext ctx) {
        return MealPlanEvaluator.evaluate(toDayPlans(ctx), ctx.groups(), ctx.dishes(), ctx.normOf(), ctx.priceOf());
    }

    MealPlanEvaluator.Result evaluateDay(LocalDate date, MenuContext ctx) {
        return MealPlanEvaluator.evaluate(
                List.of(dayPlan(date, ctx)), ctx.groups(), ctx.dishes(), ctx.normOf(), ctx.priceOf());
    }
}
