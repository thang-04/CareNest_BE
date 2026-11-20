package com.carenest.service.meals.impl;

import com.carenest.config.MenuAiProperties;
import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.FoodInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.Norm;
import com.carenest.service.meals.impl.MealPlanEvaluator.Portion;
import com.carenest.service.meals.impl.MealPlanEvaluator.Range;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodOrigin;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Danh mục giả cho test gợi ý thực đơn: một nhóm tuổi mẫu giáo (trưa + chiều), định mức 300–600 kcal, P 12–25%, L
 * 15–35%, G 40–70%, giá suất 30.000đ. Cơm + thịt + sữa = 430 kcal, đạt; cơm + cá + chuối thiếu béo; bánh gạo vượt kcal.
 */
final class MenuFixtures {

    static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    static final long AG = 3L;
    static final AgeGroupInfo GROUP = new AgeGroupInfo(
            AG, "Mẫu giáo bé (giả)", MenuBlock.KINDERGARTEN, List.of(MealSession.LUNCH, MealSession.AFTERNOON));

    static final long COM = 10L;
    static final long THIT = 11L;
    static final long CA = 12L;
    static final long DAU = 13L;
    static final long SUA = 14L;
    static final long CHUOI = 15L;
    static final long BANH = 16L;

    static final MenuSlot LUNCH_CARB = new MenuSlot(MealSession.LUNCH, 0, Set.of(DishType.CARB));
    static final MenuSlot LUNCH_MAIN = new MenuSlot(MealSession.LUNCH, 1, Set.of(DishType.MAIN));
    static final MenuSlot AFTERNOON = new MenuSlot(MealSession.AFTERNOON, 0, Set.of(DishType.SNACK));
    static final List<MenuSlot> SLOTS = List.of(LUNCH_CARB, LUNCH_MAIN, AFTERNOON);

    private static final FoodInfo RICE = food(1L, "350", "7", "1", "77", FoodOrigin.PLANT);
    private static final FoodInfo PORK = food(2L, "250", "20", "18", "0", FoodOrigin.ANIMAL);
    private static final FoodInfo FISH = food(3L, "100", "18", "3", "0", FoodOrigin.ANIMAL);
    private static final FoodInfo TOFU = food(4L, "95", "11", "5", "1", FoodOrigin.PLANT);
    private static final FoodInfo MILK = food(5L, "60", "3", "3", "5", FoodOrigin.ANIMAL);
    private static final FoodInfo BANANA = food(6L, "90", "1", "0.3", "23", FoodOrigin.PLANT);

    static final Map<Long, DishInfo> DISHES = Map.of(
            COM, dish(COM, "Cơm (giả)", DishType.CARB, RICE, 60),
            THIT, dish(THIT, "Thịt rim (giả)", DishType.MAIN, PORK, 40),
            CA, dish(CA, "Cá hấp (giả)", DishType.MAIN, FISH, 60),
            DAU, dish(DAU, "Đậu sốt (giả)", DishType.MAIN, TOFU, 80),
            SUA, dish(SUA, "Sữa (giả)", DishType.SNACK, MILK, 200),
            CHUOI, dish(CHUOI, "Chuối (giả)", DishType.SNACK, BANANA, 100),
            BANH, dish(BANH, "Bánh gạo (giả)", DishType.SNACK, RICE, 200));

    private MenuFixtures() {}

    static Norm norm(String kcalMin, String kcalMax) {
        return new Norm(
                new BigDecimal("30000"),
                range(kcalMin, kcalMax),
                range("12", "25"),
                range("15", "35"),
                range("40", "70"),
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Range.NONE,
                Map.of(),
                null);
    }

    static MenuContext ctx() {
        return ctx(DISHES, norm("300", "600"), Set.of(), Set.of(), Map.of());
    }

    static MenuContext ctx(
            Map<Long, DishInfo> dishes,
            Norm norm,
            Set<LocalDate> offDays,
            Set<Long> recent,
            Map<Integer, Map<String, Long>> template) {
        return ctx(dishes, norm, offDays, recent, MenuContext.Signals.NONE, template);
    }

    static MenuContext ctx(
            Map<Long, DishInfo> dishes,
            Norm norm,
            Set<LocalDate> offDays,
            Set<Long> recent,
            MenuContext.Signals signals,
            Map<Integer, Map<String, Long>> template) {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            dates.add(MONDAY.plusDays(i));
        }
        Map<String, List<Long>> candidates = MenuCandidateBuilder.build(
                dishes.values(),
                List.of(GROUP),
                SLOTS,
                recent,
                signals,
                MenuAiProperties.defaults().candidateLimit());
        return new MenuContext(
                MenuBlock.KINDERGARTEN,
                dates,
                offDays,
                List.of(GROUP),
                dishes,
                (groupId, date) -> norm,
                SLOTS,
                candidates,
                recent,
                signals,
                template,
                "Mùa đông",
                null);
    }

    /** Cùng một bộ món cho mọi ngày phục vụ. */
    static MenuGrid sameEveryDay(MenuContext ctx, long carb, long main, long snack) {
        Map<LocalDate, Map<String, Long>> cells = new LinkedHashMap<>();
        for (LocalDate date : ctx.servedDates()) {
            cells.put(date, Map.of(LUNCH_CARB.key(), carb, LUNCH_MAIN.key(), main, AFTERNOON.key(), snack));
        }
        return new MenuGrid(cells, Map.of());
    }

    static DishInfo dish(long id, String name, DishType type, FoodInfo food, int grams) {
        return new DishInfo(id, name, type, Map.of(AG, List.of(new Portion(food, BigDecimal.valueOf(grams)))));
    }

    static FoodInfo food(long id, String kcal, String p, String l, String g, FoodOrigin origin) {
        return new FoodInfo(
                id,
                new BigDecimal(kcal),
                new BigDecimal(p),
                new BigDecimal(l),
                new BigDecimal(g),
                origin,
                new BigDecimal("20000"),
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                List.of());
    }

    private static Range range(String min, String max) {
        return new Range(new BigDecimal(min), new BigDecimal(max));
    }
}
