package com.carenest.service.meals.impl;

import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.FoodInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.Portion;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bước 2 của cổng giá trị: lọc cứng món ứng viên cho từng ô (NUT-05, NUT-09, NUT-24). Món phải đúng loại của ô, có
 * định lượng cho mọi nhóm tuổi của khối và thực phẩm đủ năng lượng, đạm, béo, bột đường, nguồn gốc ĐV/TV. Không lọc
 * theo chất dị ứng: thực đơn chung không đổi, dị ứng nặng xử lý bằng món thay thế (NUT-23).
 */
final class MenuCandidateBuilder {

    private MenuCandidateBuilder() {}

    /**
     * Khóa ô → tối đa {@code limit} món theo thứ tự ưu tiên tất định: chưa dùng 2 tuần trước, kho khô còn hàng, hợp mùa
     * (có trong bộ mẫu theo mùa), trường đã từng dùng, rồi tới id. Ô không có món nào vẫn có khóa, danh sách rỗng.
     */
    static Map<String, List<Long>> build(
            Collection<DishInfo> activeDishes,
            List<AgeGroupInfo> groups,
            List<MenuSlot> slots,
            Set<Long> recentDishIds,
            MenuContext.Signals signals,
            int limit) {
        List<DishInfo> complete =
                activeDishes.stream().filter(d -> complete(d, groups)).toList();
        Map<String, List<Long>> candidates = new LinkedHashMap<>();
        for (MenuSlot slot : slots) {
            candidates.put(
                    slot.key(),
                    complete.stream()
                            .filter(d -> slot.types().contains(d.type()))
                            .sorted(priority(recentDishIds, signals))
                            .limit(limit)
                            .map(DishInfo::id)
                            .toList());
        }
        return candidates;
    }

    private static Comparator<DishInfo> priority(Set<Long> recent, MenuContext.Signals signals) {
        return Comparator.comparing((DishInfo d) -> recent.contains(d.id()))
                .thenComparing(d -> signals.outOfStock().contains(d.id()))
                .thenComparing(d -> !signals.seasonal().contains(d.id()))
                .thenComparing(d -> !signals.familiar().contains(d.id()))
                .thenComparing(DishInfo::id);
    }

    static boolean complete(DishInfo dish, List<AgeGroupInfo> groups) {
        if (groups.isEmpty()) {
            return false;
        }
        for (AgeGroupInfo group : groups) {
            List<Portion> portions = dish.byAgeGroup().getOrDefault(group.id(), List.of());
            boolean hasFood = portions.stream()
                    .anyMatch(p -> p.grams() != null && p.grams().signum() > 0);
            if (!hasFood || !portions.stream().allMatch(MenuCandidateBuilder::portionComplete)) {
                return false;
            }
        }
        return true;
    }

    private static boolean portionComplete(Portion portion) {
        FoodInfo food = portion.food();
        return portion.grams() != null
                && portion.grams().signum() >= 0
                && food.kcal() != null
                && food.protein() != null
                && food.lipid() != null
                && food.glucid() != null
                && food.origin() != null;
    }
}
