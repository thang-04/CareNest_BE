package com.carenest.service.meals.impl;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealCountLine;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.repository.meals.DishRepository;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * NUT-07: nhu cầu thực phẩm của một ngày thực đơn = Σ bữa × nhóm tuổi (suất × gam/trẻ của món theo nhóm tuổi) ÷
 * (1 − thải bỏ%), làm tròn 2 chữ số theo thực phẩm. Dùng chung cho trừ kho khô (suất đã chốt), bản dự trù hàng tươi
 * (sĩ số dự kiến) và kiểm nhận lần 2 (suất đã chốt); chỉ khác nguồn suất và loại bảo quản. Suất thay thế tính theo
 * món chuẩn (chưa có món thay thế dị ứng).
 */
@Component
@RequiredArgsConstructor
class MealDemandCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DishRepository dishRepository;
    private final ClassroomService classroomService;
    private final AgeGroupService ageGroupService;

    /**
     * NUT-07, NUT-18 (user chốt 03/11/2026): lượng mua = lượng ăn ÷ (1 − thải bỏ%), chưa làm tròn. Dùng chung cho chi phí
     * món, chi phí suất của bảng kiểm, thống kê thực đơn và nhu cầu kho.
     */
    static BigDecimal purchased(BigDecimal edible, BigDecimal wastePct) {
        if (wastePct == null || wastePct.signum() <= 0 || wastePct.compareTo(HUNDRED) >= 0) {
            return edible;
        }
        return edible.divide(
                BigDecimal.ONE.subtract(wastePct.divide(HUNDRED, 10, RoundingMode.HALF_UP)), 10, RoundingMode.HALF_UP);
    }

    /** Số suất của một bữa cho một nhóm tuổi. */
    record Servings(MealSession session, Long ageGroupId, int portions) {}

    /** Suất đã chốt theo (bữa, nhóm tuổi) của sổ báo ăn một điểm trường–ngày; sổ chưa xác nhận không tính. */
    List<Servings> confirmedServings(List<MealCount> counts) {
        List<MealCount> confirmed =
                counts.stream().filter(MealCount::isConfirmed).toList();
        if (confirmed.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> ageGroupOfClass = new HashMap<>();
        for (ClassroomInfoResponse c :
                classroomService.findYearClassrooms(confirmed.getFirst().getSchoolYearId())) {
            if (c.ageGroupId() != null) {
                ageGroupOfClass.put(c.id(), c.ageGroupId());
            }
        }
        Map<String, Servings> merged = new HashMap<>();
        for (MealCount count : confirmed) {
            for (MealCountLine line : count.getLines()) {
                Long ageGroupId = ageGroupOfClass.get(line.getClassroomId());
                // Dòng chờ xác nhận lại chưa phải số chốt: bếp và kho theo số đã xác nhận (NUT-03)
                if (line.isPending() || ageGroupId == null) {
                    continue;
                }
                merged.merge(
                        count.getSession() + "|" + ageGroupId,
                        new Servings(count.getSession(), ageGroupId, line.getNormal() + line.getSubstitute()),
                        (a, b) -> new Servings(a.session(), a.ageGroupId(), a.portions() + b.portions()));
            }
        }
        return List.copyOf(merged.values());
    }

    /** Khối thực đơn của từng nhóm tuổi (NUT-13). */
    Map<Long, MenuBlock> blockOfAgeGroup() {
        return ageGroupService.getAgeGroups().stream()
                .collect(Collectors.toMap(AgeGroupDetailResponse::id, g -> MenuBlock.ofAgeGroup(g.ageToMonths())));
    }

    /** Món của các ngày thực đơn kèm định lượng, thực phẩm: nạp một lần cho cả kỳ. */
    Map<Long, Dish> dishesOf(Collection<MealPlanDay> days) {
        Set<Long> dishIds = days.stream()
                .flatMap(d -> d.getItems().stream())
                .map(MealPlanItem::getDishId)
                .collect(Collectors.toSet());
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        return dishRepository.findByIdIn(dishIds).stream().collect(Collectors.toMap(Dish::getId, Function.identity()));
    }

    /** Nhu cầu một ngày khi chỉ cần tính một ngày (nạp món, khối ngay trong lời gọi). */
    Map<Long, BigDecimal> demand(MealPlanDay day, Collection<Servings> servings, FoodStorage storage) {
        if (servings.isEmpty()) {
            return Map.of();
        }
        return compute(day, dishesOf(List.of(day)), blockOfAgeGroup(), servings, storage);
    }

    /** foodId → lượng cần mua (đơn vị gốc) của ngày, chỉ thực phẩm loại {@code storage}. */
    static Map<Long, BigDecimal> compute(
            MealPlanDay day,
            Map<Long, Dish> dishes,
            Map<Long, MenuBlock> blockOfAgeGroup,
            Collection<Servings> servings,
            FoodStorage storage) {
        Map<Long, BigDecimal> edible = new HashMap<>();
        Map<Long, Food> foodById = new HashMap<>();
        for (MealPlanItem item : day.getItems()) {
            Dish dish = dishes.get(item.getDishId());
            if (dish == null) {
                continue;
            }
            for (Servings serving : servings) {
                if (serving.session() != item.getSession()
                        || blockOfAgeGroup.get(serving.ageGroupId()) != item.getMenuBlock()
                        || serving.portions() <= 0) {
                    continue;
                }
                for (DishIngredient ingredient : dish.getIngredients()) {
                    Food food = ingredient.getFood();
                    if (!Objects.equals(ingredient.getAgeGroupId(), serving.ageGroupId())
                            || food.getStorage() != storage) {
                        continue;
                    }
                    foodById.put(food.getId(), food);
                    edible.merge(
                            food.getId(),
                            ingredient.getGramsPerChild().multiply(BigDecimal.valueOf(serving.portions())),
                            BigDecimal::add);
                }
            }
        }
        Map<Long, BigDecimal> purchase = new HashMap<>();
        edible.forEach((foodId, qty) -> purchase.put(
                foodId, purchased(qty, foodById.get(foodId).getWastePct()).setScale(2, RoundingMode.HALF_UP)));
        return purchase;
    }
}
