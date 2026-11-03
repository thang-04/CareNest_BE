package com.carenest.service.meals.impl;

import com.carenest.dto.meals.KitchenDayResponse;
import com.carenest.dto.meals.StockFoodRef;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealCountLine;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.StockMapper;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.repository.meals.FoodSampleRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.KitchenDayService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KitchenDayServiceImpl implements KitchenDayService {
    private static final String LOG_PREFIX = "[KitchenDayServiceImpl]";
    private static final String NONE = "NONE";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String PARTIAL = "PARTIAL";

    private final MealPlanWeekRepository weekRepository;
    private final MealCountRepository mealCountRepository;
    private final DishRepository dishRepository;
    private final FoodRepository foodRepository;
    private final FoodSampleRepository campusLookup;
    private final MealDemandCalculator demandCalculator;
    private final ClassroomService classroomService;
    private final KitchenAccess kitchenAccess;
    private final StockMapper stockMapper;
    private final Clock clock;

    @Override
    public KitchenDayResponse getKitchenDay(CurrentUser currentUser, LocalDate date, Long campusId) {
        String logPrefix = LOG_PREFIX + "|getKitchenDay|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            Long campus = kitchenAccess.resolveReadCampus(currentUser, campusId);
            if (!campusLookup.campusExists(campus)) {
                throw GlobalException.of(ApiCode.CAMPUS_NOT_FOUND);
            }
            LocalDate day = date == null ? LocalDate.now(clock) : date;
            KitchenDayResponse response = build(campus, day);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private KitchenDayResponse build(Long campusId, LocalDate date) {
        MealPlanWeek week =
                weekRepository.findByWeekStart(date.with(DayOfWeek.MONDAY)).orElse(null);
        MealPlanDay planDay = week == null ? null : week.day(date).orElse(null);
        boolean dayOff = date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY
                || (planDay != null && planDay.isOff());
        String menuStatus = week == null ? NONE : week.getStatus().name();
        // Bếp chỉ làm theo thực đơn đã công bố; tuần nháp không hiện món
        MealPlanDay publishedDay = week != null && week.isPublished() && !dayOff ? planDay : null;

        List<MealCount> counts = mealCountRepository.findByCampusIdAndDate(campusId, date);
        List<MealCount> confirmed =
                counts.stream().filter(MealCount::isConfirmed).toList();
        String countStatus = confirmed.isEmpty() ? NONE : confirmed.size() == counts.size() ? CONFIRMED : PARTIAL;
        Instant confirmedAt = confirmed.stream()
                .map(MealCount::getConfirmedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new KitchenDayResponse(
                campusId,
                date,
                dayOff,
                menuStatus,
                countStatus,
                confirmedAt,
                publishedDay == null ? List.of() : menu(publishedDay),
                servings(confirmed),
                publishedDay == null ? List.of() : foodNeeds(publishedDay, counts));
    }

    private List<KitchenDayResponse.SessionMenu> menu(MealPlanDay day) {
        Set<Long> dishIds = day.getItems().stream().map(MealPlanItem::getDishId).collect(Collectors.toSet());
        Map<Long, Dish> dishes = dishIds.isEmpty()
                ? Map.of()
                : dishRepository.findAllById(dishIds).stream()
                        .collect(Collectors.toMap(Dish::getId, Function.identity()));
        record Slot(MealSession session, MenuBlock block) {}
        Map<Slot, List<KitchenDayResponse.MenuDish>> bySlot = new LinkedHashMap<>();
        for (MealPlanItem item : day.getItems()) {
            Dish dish = dishes.get(item.getDishId());
            if (dish == null) {
                continue;
            }
            bySlot.computeIfAbsent(new Slot(item.getSession(), item.getMenuBlock()), slot -> new ArrayList<>())
                    .add(new KitchenDayResponse.MenuDish(dish.getId(), dish.getCode(), dish.getName()));
        }
        return bySlot.entrySet().stream()
                .sorted(Comparator.comparing(
                                (Map.Entry<Slot, ?> e) -> e.getKey().session())
                        .thenComparing(e -> e.getKey().block()))
                .map(e -> new KitchenDayResponse.SessionMenu(
                        e.getKey().session(), e.getKey().block(), e.getValue()))
                .toList();
    }

    /** NUT-03: chỉ dòng đã xác nhận (visibleLines(false)); số đang chờ xác nhận lại không hiện. Không có tên/ID trẻ. */
    private List<KitchenDayResponse.ClassServings> servings(List<MealCount> confirmed) {
        Map<Long, Map<Long, String>> classNamesByYear = new HashMap<>();
        List<KitchenDayResponse.ClassServings> result = new ArrayList<>();
        for (MealCount count : confirmed) {
            Map<Long, String> names = classNamesByYear.computeIfAbsent(
                    count.getSchoolYearId(),
                    year -> classroomService.findYearClassrooms(year).stream()
                            .collect(Collectors.toMap(ClassroomInfoResponse::id, ClassroomInfoResponse::name)));
            for (MealCountLine line : count.visibleLines(false)) {
                result.add(new KitchenDayResponse.ClassServings(
                        count.getSession(),
                        line.getClassroomId(),
                        names.getOrDefault(line.getClassroomId(), ""),
                        line.getNormal(),
                        line.getSubstitute()));
            }
        }
        return result.stream()
                .sorted(Comparator.comparing(KitchenDayResponse.ClassServings::session)
                        .thenComparing(KitchenDayResponse.ClassServings::classroomName))
                .toList();
    }

    /** NUT-07: nhu cầu thực phẩm theo suất đã chốt, cùng công thức trừ kho khô và kiểm nhận hàng tươi lần 2. */
    private List<KitchenDayResponse.FoodNeed> foodNeeds(MealPlanDay day, List<MealCount> counts) {
        List<MealDemandCalculator.Servings> servings = demandCalculator.confirmedServings(counts);
        if (servings.isEmpty()) {
            return List.of();
        }
        Map<FoodStorage, Map<Long, BigDecimal>> byStorage = new LinkedHashMap<>();
        for (FoodStorage storage : List.of(FoodStorage.FRESH, FoodStorage.DRY)) {
            byStorage.put(storage, demandCalculator.demand(day, servings, storage));
        }
        Set<Long> foodIds =
                byStorage.values().stream().flatMap(m -> m.keySet().stream()).collect(Collectors.toSet());
        if (foodIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Food> foods = foodRepository.findAllById(foodIds).stream()
                .collect(Collectors.toMap(Food::getId, Function.identity()));
        List<KitchenDayResponse.FoodNeed> needs = new ArrayList<>();
        byStorage.forEach((storage, demand) -> demand.forEach((foodId, qty) -> {
            StockFoodRef ref = stockMapper.toFoodRef(foods.get(foodId));
            if (ref != null && qty.signum() > 0) {
                needs.add(new KitchenDayResponse.FoodNeed(ref, storage.name(), qty));
            }
        }));
        return needs.stream()
                .sorted(Comparator.comparing(
                        (KitchenDayResponse.FoodNeed n) -> n.food().name()))
                .toList();
    }
}
