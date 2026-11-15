package com.carenest.service.meals.impl;

import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.FoodSample;
import com.carenest.entity.meals.FoodSampleDishDetail;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.FoodSampleRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Khung dòng của biên bản dựng từ dữ liệu nguồn (INS-01): món theo thực đơn đã công bố, suất đã xác nhận tách Nhà trẻ /
 * Mẫu giáo theo khối thực đơn của nhóm tuổi (NUT-13), bản lưu mẫu của bếp. Chỉ đọc; ô PHT nhập để trống.
 */
@Component
@RequiredArgsConstructor
class InspectionSkeleton {
    private static final Map<MealSession, String> SESSION_LABELS = new EnumMap<>(Map.of(
            MealSession.MORNING, "Bữa phụ sáng",
            MealSession.LUNCH, "Bữa trưa",
            MealSession.AFTERNOON, "Bữa chiều"));

    private final MealPlanWeekRepository weekRepository;
    private final MealCountRepository mealCountRepository;
    private final DishRepository dishRepository;
    private final FoodSampleRepository sampleRepository;
    private final MealDemandCalculator demandCalculator;
    private final InspectionIntakeSkeleton intake;

    List<Map<String, Object>> rows(InspectionTab tab, Long campusId, LocalDate date) {
        return switch (tab) {
            case PROCESS, SERVE -> dishRows(campusId, date);
            case SAMPLE -> sampleRows(campusId, date);
            case FRESH -> intake.fresh(campusId, date);
            case DRY -> intake.dry(campusId, date);
        };
    }

    private List<Map<String, Object>> dishRows(Long campusId, LocalDate date) {
        MealPlanDay day = publishedDay(date);
        if (day == null) {
            return List.of();
        }
        Map<Long, Dish> dishes =
                dishes(day.getItems().stream().map(MealPlanItem::getDishId).collect(Collectors.toSet()));
        Map<String, Integer> servings = servingsBySessionBlock(campusId, date);
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        day.getItems().stream()
                .sorted(Comparator.comparing(MealPlanItem::getSession).thenComparing(MealPlanItem::getMenuBlock))
                .forEach(item -> {
                    Dish dish = dishes.get(item.getDishId());
                    if (dish == null) {
                        return;
                    }
                    String key = item.getSession() + ":" + item.getMenuBlock() + ":" + item.getDishId();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(InspectionColumns.KEY, key);
                    row.put("session", item.getSession().name());
                    row.put("block", item.getMenuBlock().name());
                    row.put("dishId", item.getDishId());
                    row.put("sessionLabel", sessionLabel(item.getSession()) + " · " + blockLabel(item.getMenuBlock()));
                    row.put("dishName", dish.getName());
                    row.put("servings", servings.getOrDefault(slot(item.getSession(), item.getMenuBlock()), 0));
                    rows.putIfAbsent(key, row);
                });
        return List.copyOf(rows.values());
    }

    private List<Map<String, Object>> sampleRows(Long campusId, LocalDate date) {
        List<FoodSample> samples = sampleRepository
                .findByCampusIdAndSampleDateBetweenOrderBySampleDateDescSampledAtDescIdDesc(campusId, date, date)
                .stream()
                .sorted(Comparator.comparing(FoodSample::getSession).thenComparing(FoodSample::getSampledAt))
                .toList();
        if (samples.isEmpty()) {
            return List.of();
        }
        Map<Long, Dish> dishes =
                dishes(samples.stream().flatMap(s -> s.getDishIds().stream()).collect(Collectors.toSet()));
        Map<String, Integer> servings = servingsBySessionBlock(campusId, date);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FoodSample sample : samples) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(InspectionColumns.KEY, "sample:" + sample.getId());
            row.put("session", sample.getSession().name());
            row.put("sessionLabel", sessionLabel(sample.getSession()));
            row.put(
                    "dishName",
                    sample.getDishIds().stream()
                            .map(dishes::get)
                            .filter(java.util.Objects::nonNull)
                            .map(Dish::getName)
                            .collect(Collectors.joining(", ")));
            // Mẫu lưu theo bữa nên số suất là tổng hai khối của bữa đó
            row.put(
                    "servings",
                    servings.getOrDefault(slot(sample.getSession(), MenuBlock.NURSERY), 0)
                            + servings.getOrDefault(slot(sample.getSession(), MenuBlock.KINDERGARTEN), 0));
            row.put("weight", detailText(sample, dishes, FoodSampleDishDetail::weight));
            row.put("container", detailText(sample, dishes, FoodSampleDishDetail::container));
            row.put("temperature", detailText(sample, dishes, FoodSampleDishDetail::temperature));
            row.put("takenAt", sample.getSampledAt().toString());
            row.put(
                    "destroyedAt",
                    sample.getDisposedAt() == null ? "" : sample.getDisposedAt().toString());
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    /**
     * Ô Mẫu 5 của một dòng sổ (một lần lấy mẫu nhiều món): mọi món cùng giá trị ⇒ ghi một lần; khác nhau ⇒ "Tên món:
     * giá trị" cách nhau dấu chấm phẩy; món chưa ghi thì bỏ qua.
     */
    private static String detailText(
            FoodSample sample, Map<Long, Dish> dishes, Function<FoodSampleDishDetail, String> field) {
        Map<Long, FoodSampleDishDetail> details = sample.getDishDetails().stream()
                .collect(Collectors.toMap(FoodSampleDishDetail::dishId, Function.identity(), (a, b) -> a));
        Map<String, String> byDish = new LinkedHashMap<>();
        for (Long dishId : sample.getDishIds()) {
            FoodSampleDishDetail detail = details.get(dishId);
            String value = detail == null ? "" : field.apply(detail);
            Dish dish = dishes.get(dishId);
            if (dish != null && !value.isBlank()) {
                byDish.put(dish.getName(), value);
            }
        }
        if (byDish.values().stream().distinct().count() <= 1) {
            return byDish.values().stream().findFirst().orElse("");
        }
        return byDish.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    /** Suất đã xác nhận theo (bữa, khối); sổ chưa xác nhận không tính (NUT-03). */
    private Map<String, Integer> servingsBySessionBlock(Long campusId, LocalDate date) {
        List<MealCount> counts = mealCountRepository.findByCampusIdAndDate(campusId, date);
        Map<Long, MenuBlock> blocks = demandCalculator.blockOfAgeGroup();
        Map<String, Integer> result = new HashMap<>();
        for (MealDemandCalculator.Servings serving : demandCalculator.confirmedServings(counts)) {
            MenuBlock block = blocks.get(serving.ageGroupId());
            if (block != null) {
                result.merge(slot(serving.session(), block), serving.portions(), Integer::sum);
            }
        }
        return result;
    }

    private MealPlanDay publishedDay(LocalDate date) {
        if (Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).contains(date.getDayOfWeek())) {
            return null;
        }
        MealPlanWeek week =
                weekRepository.findByWeekStart(date.with(DayOfWeek.MONDAY)).orElse(null);
        if (week == null || !week.isPublished()) {
            return null;
        }
        MealPlanDay day = week.day(date).orElse(null);
        return day == null || day.isOff() ? null : day;
    }

    private Map<Long, Dish> dishes(Set<Long> ids) {
        return ids.isEmpty()
                ? Map.of()
                : dishRepository.findAllById(ids).stream().collect(Collectors.toMap(Dish::getId, Function.identity()));
    }

    private static String slot(MealSession session, MenuBlock block) {
        return session + "|" + block;
    }

    private static String sessionLabel(MealSession session) {
        return SESSION_LABELS.get(session);
    }

    private static String blockLabel(MenuBlock block) {
        return block == MenuBlock.NURSERY ? "Nhà trẻ" : "Mẫu giáo";
    }
}
