package com.carenest.service.meals.impl;

import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.Norm;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Ngữ cảnh gợi ý thực đơn tuần của một khối, đã đọc xong từ DB (bước 1–2 của cổng giá trị): engine chỉ tính, không
 * I/O. {@code template}: thứ (0 = thứ Hai) → khóa ô → món theo thực đơn mẫu; {@code candidates}: khóa ô → món ứng
 * viên theo thứ tự ưu tiên.
 */
record MenuContext(
        MenuBlock block,
        List<LocalDate> dates,
        Set<LocalDate> offDays,
        List<AgeGroupInfo> groups,
        Map<Long, DishInfo> dishes,
        BiFunction<Long, LocalDate, Norm> normOf,
        List<MenuSlot> slots,
        Map<String, List<Long>> candidates,
        Set<Long> recentDishIds,
        Signals signals,
        Map<Integer, Map<String, Long>> template,
        String season,
        String wishes,
        Set<Long> requiredDishIds,
        boolean keepTemplate,
        BiFunction<Long, LocalDate, BigDecimal> priceOf) {

    /** Ngữ cảnh có món bắt buộc / giữ mẫu nhưng chưa có sổ giá: giá lấy từ {@code FoodInfo.unitPrice}. */
    MenuContext(
            MenuBlock block,
            List<LocalDate> dates,
            Set<LocalDate> offDays,
            List<AgeGroupInfo> groups,
            Map<Long, DishInfo> dishes,
            BiFunction<Long, LocalDate, Norm> normOf,
            List<MenuSlot> slots,
            Map<String, List<Long>> candidates,
            Set<Long> recentDishIds,
            Signals signals,
            Map<Integer, Map<String, Long>> template,
            String season,
            String wishes,
            Set<Long> requiredDishIds,
            boolean keepTemplate) {
        this(
                block,
                dates,
                offDays,
                groups,
                dishes,
                normOf,
                slots,
                candidates,
                recentDishIds,
                signals,
                template,
                season,
                wishes,
                requiredDishIds,
                keepTemplate,
                null);
    }

    /** Ngữ cảnh không có ràng buộc của người lập: không món bắt buộc, không giữ ô theo mẫu. */
    MenuContext(
            MenuBlock block,
            List<LocalDate> dates,
            Set<LocalDate> offDays,
            List<AgeGroupInfo> groups,
            Map<Long, DishInfo> dishes,
            BiFunction<Long, LocalDate, Norm> normOf,
            List<MenuSlot> slots,
            Map<String, List<Long>> candidates,
            Set<Long> recentDishIds,
            Signals signals,
            Map<Integer, Map<String, Long>> template,
            String season,
            String wishes) {
        this(
                block,
                dates,
                offDays,
                groups,
                dishes,
                normOf,
                slots,
                candidates,
                recentDishIds,
                signals,
                template,
                season,
                wishes,
                Set.of(),
                false,
                null);
    }

    MenuContext {
        priceOf = priceOf == null ? (foodId, date) -> null : priceOf;
        requiredDishIds = Set.copyOf(requiredDishIds);
        dates = List.copyOf(dates);
        offDays = Set.copyOf(offDays);
        groups = List.copyOf(groups);
        dishes = Map.copyOf(dishes);
        slots = List.copyOf(slots);
        candidates = Map.copyOf(candidates);
        recentDishIds = Set.copyOf(recentDishIds);
        signals = signals == null ? Signals.NONE : signals;
        template = Map.copyOf(template);
    }

    /**
     * Tín hiệu chọn món từ dữ liệu của trường (không phải rule chặn): {@code seasonal} = món có trong bộ thực đơn mẫu
     * trùng nhãn mùa PHT chọn (NUT-13), {@code seasonTemplate} = nhãn bộ đó (null khi không có); {@code familiar} =
     * món đã có trong kế hoạch tuần đã công bố gần đây; {@code outOfStock} = món dùng thực phẩm kho khô mà mọi điểm
     * trường đang hết hàng còn hạn (NUT-11).
     */
    record Signals(Set<Long> seasonal, String seasonTemplate, Set<Long> familiar, Set<Long> outOfStock) {
        static final Signals NONE = new Signals(Set.of(), null, Set.of(), Set.of());

        Signals {
            seasonal = Set.copyOf(seasonal);
            familiar = Set.copyOf(familiar);
            outOfStock = Set.copyOf(outOfStock);
        }
    }

    /** Cùng ngữ cảnh nhưng chỉ coi {@code date} là ngày phục vụ: bảng kiểm của thay một món chỉ nói về ngày đó. */
    MenuContext onlyDay(LocalDate date) {
        Set<LocalDate> others = new HashSet<>(dates);
        others.remove(date);
        return new MenuContext(
                block,
                dates,
                others,
                groups,
                dishes,
                normOf,
                slots,
                candidates,
                recentDishIds,
                signals,
                template,
                season,
                wishes,
                requiredDishIds,
                keepTemplate,
                priceOf);
    }

    /** Cùng ngữ cảnh nhưng giá thực phẩm lấy từ sổ giá theo ngày (NUT-27), như kế hoạch tuần. */
    MenuContext priced(BiFunction<Long, LocalDate, BigDecimal> priceOf) {
        return new MenuContext(
                block,
                dates,
                offDays,
                groups,
                dishes,
                normOf,
                slots,
                candidates,
                recentDishIds,
                signals,
                template,
                season,
                wishes,
                requiredDishIds,
                keepTemplate,
                priceOf);
    }

    /**
     * Cùng ngữ cảnh kèm ràng buộc của người lập: {@code required} = món phải có ít nhất một lần trong tuần; {@code
     * keepTemplate} = chỉnh từ thực đơn mẫu, ô có món mẫu dùng được thì giữ.
     */
    MenuContext constrained(Set<Long> required, boolean keepTemplate) {
        return new MenuContext(
                block,
                dates,
                offDays,
                groups,
                dishes,
                normOf,
                slots,
                candidates,
                recentDishIds,
                signals,
                template,
                season,
                wishes,
                required,
                keepTemplate,
                priceOf);
    }

    /** Món của thực đơn mẫu tại ô {@code slot} của ngày {@code date}; null khi mẫu không có món ở đó. */
    Long templateDish(LocalDate date, MenuSlot slot) {
        return template.getOrDefault(dates.indexOf(date), Map.of()).get(slot.key());
    }

    /** Ngày phục vụ (không nghỉ) theo thứ tự trong tuần. */
    List<LocalDate> servedDates() {
        return dates.stream().filter(d -> !offDays.contains(d)).toList();
    }

    List<Long> candidatesOf(MenuSlot slot) {
        return candidates.getOrDefault(slot.key(), List.of());
    }
}
