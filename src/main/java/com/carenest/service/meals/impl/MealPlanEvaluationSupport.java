package com.carenest.service.meals.impl;

import com.carenest.config.NutritionConversionProperties;
import com.carenest.dto.meals.NutritionConversionResponse.ConvertedGrams;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.AgeGroupMealConfig;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.Food;
import com.carenest.repository.meals.AgeGroupMealConfigRepository;
import com.carenest.service.meals.FoodPriceService;
import com.carenest.service.meals.FoodPriceService.FoodPriceBook;
import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.EnergyFactors;
import com.carenest.service.meals.impl.MealPlanEvaluator.FoodInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.Norm;
import com.carenest.service.meals.impl.MealPlanEvaluator.Portion;
import com.carenest.service.meals.impl.MealPlanEvaluator.Range;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.NutritionConversion;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import com.carenest.utils.enums.Micronutrient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Dựng input cho {@link MealPlanEvaluator} từ dữ liệu bán trú: nhóm tuổi được kiểm, món, định mức hiệu lực. Kế hoạch
 * tuần và nháp gợi ý thực đơn dùng chung để cùng một lưới luôn cho cùng kết quả kiểm (NUT-08, NUT-09).
 */
@Component
@RequiredArgsConstructor
class MealPlanEvaluationSupport {

    static final int SCHOOL_DAYS = 5;

    private final AgeGroupService ageGroupService;
    private final ClassroomService classroomService;
    private final AgeGroupMealConfigRepository configRepository;
    private final FoodPriceService foodPriceService;
    private final NutritionConversionProperties conversion;

    /** Nhóm tuổi có lớp trong năm học hiện hành; chưa có lớp nào thì kiểm mọi nhóm tuổi. */
    List<AgeGroupInfo> planningGroups() {
        Set<Long> disabled = configRepository.findDisabledNutritionGroupIds();
        return evaluatedGroups().stream()
                .filter(g -> !disabled.contains(g.id()))
                .toList();
    }

    Collection<AgeGroupDetailResponse> planningAgeGroups() {
        Set<Long> disabled = configRepository.findDisabledNutritionGroupIds();
        return ageGroupService.getAgeGroups().stream()
                .filter(g -> !disabled.contains(g.id()))
                .toList();
    }

    List<AgeGroupInfo> evaluatedGroups() {
        Set<Long> withClasses = classroomService.findActiveYearClassrooms().stream()
                .map(ClassroomInfoResponse::ageGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return ageGroupService.getAgeGroups().stream()
                .filter(g -> withClasses.isEmpty() || withClasses.contains(g.id()))
                .filter(g -> g.mealSessions() != null && !g.mealSessions().isEmpty())
                .map(g -> new AgeGroupInfo(
                        g.id(), g.name(), MenuBlock.ofAgeGroup(g.ageToMonths()), List.copyOf(g.mealSessions())))
                .toList();
    }

    /** Định mức hiệu lực của nhóm tuổi tại ngày (NUT-17), đọc một lần mọi bản có hiệu lực tới {@code lastDay}. */
    BiFunction<Long, LocalDate, Norm> normsUntil(LocalDate lastDay) {
        Map<Long, List<AgeGroupMealConfig>> configs =
                configRepository.findByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(lastDay).stream()
                        .collect(Collectors.groupingBy(AgeGroupMealConfig::getAgeGroupId));
        EnergyFactors factors = new EnergyFactors(
                conversion.proteinKcalPerG(), conversion.lipidKcalPerG(), conversion.glucidKcalPerG());
        NutritionConversion converter = new NutritionConversion(conversion);
        return (ageGroupId, date) -> configs.getOrDefault(ageGroupId, List.of()).stream()
                .filter(c -> !c.getEffectiveFrom().isAfter(date))
                .findFirst()
                .map(c -> toNorm(c, converter).withFactors(factors))
                .orElse(null);
    }

    /** Bản định mức hiệu lực tới {@code lastDay}: dùng để nhận biết định mức đã đổi giữa lúc tạo và dùng nháp. */
    List<AgeGroupMealConfig> configsUntil(LocalDate lastDay) {
        return configRepository.findByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(lastDay);
    }

    static Map<MenuBlock, Set<MealSession>> servedSessions(Collection<AgeGroupDetailResponse> ageGroups) {
        Map<MenuBlock, Set<MealSession>> served = new EnumMap<>(MenuBlock.class);
        for (MenuBlock block : MenuBlock.values()) {
            Set<MealSession> sessions = new TreeSet<>();
            ageGroups.stream()
                    .filter(g -> MenuBlock.ofAgeGroup(g.ageToMonths()) == block && g.mealSessions() != null)
                    .forEach(g -> sessions.addAll(g.mealSessions()));
            if (!sessions.isEmpty()) {
                served.put(block, sessions);
            }
        }
        return served;
    }

    /**
     * Giá đơn vị mua của thực phẩm các món tại ngày thực đơn (NUT-27): cùng sổ giá với kế hoạch tuần nên một lưới luôn ra
     * cùng tiền ăn ở cả hai nơi. Đọc một lần theo tập thực phẩm của {@code dishes}.
     */
    BiFunction<Long, LocalDate, BigDecimal> priceOf(Collection<Dish> dishes) {
        FoodPriceBook book = foodPriceService.book(dishes.stream()
                .flatMap(d -> d.getIngredients().stream())
                .map(i -> i.getFood().getId())
                .collect(Collectors.toSet()));
        return book::price;
    }

    static Map<Long, DishInfo> dishInfos(Collection<Dish> dishes) {
        return dishes.stream().collect(Collectors.toMap(Dish::getId, MealPlanEvaluationSupport::toDishInfo));
    }

    static DishInfo toDishInfo(Dish dish) {
        Map<Long, List<Portion>> byAgeGroup = new HashMap<>();
        for (DishIngredient ingredient : dish.getIngredients()) {
            Food food = ingredient.getFood();
            FoodInfo info = foodInfo(food, food.getPurchaseUnitSize(), food.getWastePct());
            byAgeGroup
                    .computeIfAbsent(ingredient.getAgeGroupId(), id -> new ArrayList<>())
                    .add(new Portion(info, ingredient.getGramsPerChild()));
        }
        return new DishInfo(dish.getId(), dish.getName(), dish.getDishType(), byAgeGroup);
    }

    /** Số liệu thực phẩm cho bộ kiểm; giá lấy riêng theo ngày nên để trống. */
    static FoodInfo foodInfo(Food food, BigDecimal purchaseUnitSize, BigDecimal wastePct) {
        return new FoodInfo(
                food.getId(),
                food.getKcal(),
                food.getProtein(),
                food.getLipid(),
                food.getGlucid(),
                food.getOrigin(),
                null,
                purchaseUnitSize,
                wastePct,
                food.getAllergens().stream().map(a -> a.getName()).sorted().toList(),
                microOf(food));
    }

    private static Map<Micronutrient, BigDecimal> microOf(Food food) {
        Map<Micronutrient, BigDecimal> micro = new EnumMap<>(Micronutrient.class);
        for (Micronutrient nutrient : Micronutrient.values()) {
            micro.put(nutrient, MicronutrientValues.of(food, nutrient));
        }
        return micro;
    }

    // Làm tròn 2 số lẻ như số hiển thị để thấy sao chấm vậy
    private static Range gramRange(BigDecimal atKcalMin, BigDecimal atKcalMax) {
        return new Range(atKcalMin.setScale(2, RoundingMode.HALF_UP), atKcalMax.setScale(2, RoundingMode.HALF_UP));
    }

    static Norm toNorm(AgeGroupMealConfig c, NutritionConversion converter) {
        // NUT-34: đã chọn cơ cấu áp dụng thì định mức gam/trẻ/ngày là hàm lượng quy đổi tại hai mốc kcal của trường
        ConvertedGrams low = converter.convert(
                c.getKcalMin(), c.getAppliedProteinPct(), c.getAppliedLipidPct(), c.getAppliedGlucidPct());
        ConvertedGrams high = converter.convert(
                c.getKcalMax(), c.getAppliedProteinPct(), c.getAppliedLipidPct(), c.getAppliedGlucidPct());
        boolean converted = low != null && high != null;
        Map<Micronutrient, Range> micro = new EnumMap<>(Micronutrient.class);
        for (Micronutrient nutrient : Micronutrient.values()) {
            micro.put(nutrient, new Range(MicronutrientValues.min(c, nutrient), MicronutrientValues.max(c, nutrient)));
        }
        Map<MealSession, Range> sessionKcalPct = new EnumMap<>(MealSession.class);
        sessionKcalPct.put(MealSession.MORNING, new Range(c.getMorningKcalPctMin(), c.getMorningKcalPctMax()));
        sessionKcalPct.put(MealSession.LUNCH, new Range(c.getLunchKcalPctMin(), c.getLunchKcalPctMax()));
        sessionKcalPct.put(MealSession.AFTERNOON, new Range(c.getAfternoonKcalPctMin(), c.getAfternoonKcalPctMax()));
        return new Norm(
                c.getMealPrice(),
                new Range(c.getKcalMin(), c.getKcalMax()),
                new Range(c.getProteinPctMin(), c.getProteinPctMax()),
                new Range(c.getLipidPctMin(), c.getLipidPctMax()),
                new Range(c.getGlucidPctMin(), c.getGlucidPctMax()),
                converted
                        ? gramRange(low.animalProteinG(), high.animalProteinG())
                        : new Range(c.getAnimalProteinGMin(), c.getAnimalProteinGMax()),
                converted
                        ? gramRange(low.plantProteinG(), high.plantProteinG())
                        : new Range(c.getPlantProteinGMin(), c.getPlantProteinGMax()),
                converted
                        ? gramRange(low.animalLipidG(), high.animalLipidG())
                        : new Range(c.getAnimalLipidGMin(), c.getAnimalLipidGMax()),
                converted
                        ? gramRange(low.plantLipidG(), high.plantLipidG())
                        : new Range(c.getPlantLipidGMin(), c.getPlantLipidGMax()),
                converted ? gramRange(low.glucidG(), high.glucidG()) : new Range(c.getGlucidGMin(), c.getGlucidGMax()),
                sessionKcalPct,
                c.getDailyKcalNeed(),
                micro,
                c.getServiceFee(),
                c.getBudgetTolerancePct(),
                EnergyFactors.DEFAULT,
                new MealPlanEvaluator.Applied(
                        c.getAppliedProteinPct(), c.getAppliedLipidPct(), c.getAppliedGlucidPct()));
    }
}
