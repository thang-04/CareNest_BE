package com.carenest.service.meals.impl;

import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MealPlanSummaryResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.AgeGroupMealConfig;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.AgeGroupMealConfigRepository;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.MealPlanService;
import com.carenest.service.meals.StockLedgerService;
import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DayPlan;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.FoodInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.ItemPlan;
import com.carenest.service.meals.impl.MealPlanEvaluator.Norm;
import com.carenest.service.meals.impl.MealPlanEvaluator.Portion;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MealPlanServiceImpl implements MealPlanService {
    private static final String LOG_PREFIX = "[MealPlanServiceImpl]";
    private static final String NONE = "NONE";
    private static final int SCHOOL_DAYS = 5;

    private final MealPlanWeekRepository weekRepository;
    private final DishRepository dishRepository;
    private final AgeGroupMealConfigRepository configRepository;
    private final MealCountRepository mealCountRepository;
    private final StockLedgerService stockLedgerService;
    private final AgeGroupService ageGroupService;
    private final ClassroomService classroomService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<MealPlanSummaryResponse> getMealPlans(CurrentUser currentUser, LocalDate from, LocalDate to) {
        String logPrefix = LOG_PREFIX + "|getMealPlans|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            LocalDate start = from == null ? today.minusWeeks(4) : from;
            LocalDate end = to == null ? today.plusWeeks(4) : to;
            List<MealPlanSummaryResponse> response =
                    weekRepository.findByWeekStartBetweenOrderByWeekStartAsc(start, end).stream()
                            .map(w -> new MealPlanSummaryResponse(
                                    w.getId(), w.getWeekStart(), w.getStatus(), w.getPublishedAt()))
                            .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MealPlanResponse getMealPlan(CurrentUser currentUser, LocalDate weekStart) {
        String logPrefix = LOG_PREFIX + "|getMealPlan|userId=" + currentUser.userId() + "|weekStart=" + weekStart;
        log.info("{}|START", logPrefix);
        try {
            checkMonday(weekStart);
            MealPlanWeek week = weekRepository.findByWeekStart(weekStart).orElse(null);
            MealPlanResponse response = toResponse(currentUser, weekStart, week);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MealPlanResponse saveMealPlan(CurrentUser currentUser, LocalDate weekStart, MealPlanRequest request) {
        String logPrefix = LOG_PREFIX + "|saveMealPlan|userId=" + currentUser.userId() + "|weekStart=" + weekStart;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            checkMonday(weekStart);
            MealPlanWeek week = weekRepository
                    .findByWeekStart(weekStart)
                    .orElseGet(() -> MealPlanWeek.draft(weekStart, currentUser.userId()));
            // NUT-08: đã công bố thì không sửa (ngoại lệ P-39 làm ở lát sau)
            if (week.isPublished()) {
                throw GlobalException.of(ApiCode.MEAL_PLAN_PUBLISHED);
            }
            week.changeDays(toDays(weekStart, request), currentUser.userId());
            week = weekRepository.saveAndFlush(week);
            MealPlanResponse response = toResponse(currentUser, weekStart, week);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MealPlanResponse publishMealPlan(CurrentUser currentUser, LocalDate weekStart) {
        String logPrefix = LOG_PREFIX + "|publishMealPlan|userId=" + currentUser.userId() + "|weekStart=" + weekStart;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            checkMonday(weekStart);
            MealPlanWeek week = weekRepository
                    .findByWeekStart(weekStart)
                    .orElseThrow(() -> GlobalException.of(ApiCode.MEAL_PLAN_NOT_PUBLISHABLE, 1, "tuần chưa lập"));
            if (week.isPublished()) {
                throw GlobalException.of(ApiCode.MEAL_PLAN_PUBLISHED);
            }
            // NUT-08: chưa đạt cân đối (hoặc chưa kiểm được) thì chặn công bố
            MealPlanEvaluator.Result result = evaluate(weekStart, week);
            List<MealPlanResponse.Issue> blocking = result.issues().stream()
                    .filter(i -> MealPlanEvaluator.BLOCKING.equals(i.severity()))
                    .toList();
            if (!blocking.isEmpty()) {
                throw GlobalException.of(
                        ApiCode.MEAL_PLAN_NOT_PUBLISHABLE,
                        blocking.size(),
                        blocking.getFirst().message());
            }
            week.publish(currentUser.userId(), clock.instant());
            weekRepository.flush();
            syncConfirmedDays(weekStart);
            MealPlanResponse response = toResponse(currentUser, weekStart, week);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // P-38: suất đã chốt trước khi tuần được công bố (hiếm) ⇒ trừ kho ngay khi công bố
    private void syncConfirmedDays(LocalDate weekStart) {
        Set<String> done = new LinkedHashSet<>();
        for (MealCount count : mealCountRepository.findByDateBetween(weekStart, weekStart.plusDays(SCHOOL_DAYS - 1))) {
            if (count.isConfirmed() && done.add(count.getCampusId() + "|" + count.getDate())) {
                stockLedgerService.syncDay(count.getCampusId(), count.getDate());
            }
        }
    }

    // AUTH-20, NUT-08: lập và công bố thực đơn cần MENU_MANAGE (cả trường)
    private static void checkCanManage(CurrentUser currentUser) {
        if (!currentUser.hasPermission(Permission.MENU_MANAGE)) {
            throw GlobalException.of(ApiCode.FORBIDDEN);
        }
    }

    private static void checkMonday(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw GlobalException.of(ApiCode.MEAL_PLAN_WEEK_INVALID);
        }
    }

    private List<MealPlanDay> toDays(LocalDate weekStart, MealPlanRequest request) {
        Map<LocalDate, MealPlanRequest.DayInput> byDate = new LinkedHashMap<>();
        for (MealPlanRequest.DayInput day : request.days()) {
            long offset = day.date().toEpochDay() - weekStart.toEpochDay();
            if (offset < 0 || offset >= SCHOOL_DAYS || byDate.putIfAbsent(day.date(), day) != null) {
                throw GlobalException.of(ApiCode.MEAL_PLAN_DATE_INVALID, day.date());
            }
        }
        Map<MenuBlock, Set<MealSession>> served = servedSessions(ageGroupService.getAgeGroups());
        Set<Long> dishIds = request.days().stream()
                .flatMap(d -> d.items().stream())
                .map(MealPlanRequest.ItemInput::dishId)
                .collect(Collectors.toSet());
        Map<Long, Dish> dishes = dishRepository.findAllById(dishIds).stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        List<MealPlanDay> days = new ArrayList<>();
        for (int i = 0; i < SCHOOL_DAYS; i++) {
            LocalDate date = weekStart.plusDays(i);
            MealPlanRequest.DayInput day = byDate.get(date);
            if (day == null) {
                days.add(MealPlanDay.of(date, false, "", List.of()));
                continue;
            }
            Set<String> seen = new LinkedHashSet<>();
            List<MealPlanItem> items = new ArrayList<>();
            for (MealPlanRequest.ItemInput item : day.items()) {
                if (!served.getOrDefault(item.block(), Set.of()).contains(item.session())) {
                    throw GlobalException.of(
                            ApiCode.MEAL_PLAN_SESSION_INVALID, blockLabel(item.block()), item.session());
                }
                Dish dish = dishes.get(item.dishId());
                if (dish == null) {
                    throw GlobalException.of(ApiCode.DISH_NOT_FOUND);
                }
                if (dish.getStatus() != CatalogStatus.ACTIVE) {
                    throw GlobalException.of(ApiCode.MEAL_PLAN_DISH_INACTIVE, dish.getName());
                }
                // Một món chỉ một lần trong cùng bữa, khối (khóa duy nhất)
                if (seen.add(item.session() + "|" + item.block() + "|" + item.dishId())) {
                    items.add(MealPlanItem.of(item.session(), item.block(), item.dishId(), items.size()));
                }
            }
            String note = day.note() == null ? "" : day.note().trim();
            days.add(MealPlanDay.of(date, day.off(), note, day.off() ? List.of() : items));
        }
        return days;
    }

    private MealPlanResponse toResponse(CurrentUser currentUser, LocalDate weekStart, MealPlanWeek week) {
        // Lưới chỉ hiện khối có nhóm tuổi được kiểm (có lớp năm học hiện hành), cùng nguồn với bảng kiểm
        List<AgeGroupInfo> groups = evaluatedGroups(ageGroupService.getAgeGroups());
        Map<MenuBlock, Set<MealSession>> served = new EnumMap<>(MenuBlock.class);
        groups.forEach(
                g -> served.computeIfAbsent(g.block(), b -> new TreeSet<>()).addAll(g.sessions()));
        List<MealPlanResponse.Block> blocks = new ArrayList<>();
        served.forEach((block, sessions) -> blocks.add(new MealPlanResponse.Block(
                block,
                List.copyOf(sessions),
                groups.stream()
                        .filter(g -> g.block() == block)
                        .map(AgeGroupInfo::id)
                        .toList())));
        List<MealPlanDay> days = week == null ? List.of() : week.getDays();
        Map<Long, Dish> dishes = dishRepository
                .findByIdIn(days.stream()
                        .flatMap(d -> d.getItems().stream())
                        .map(MealPlanItem::getDishId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        List<MealPlanResponse.Day> dayViews = new ArrayList<>();
        for (int i = 0; i < SCHOOL_DAYS; i++) {
            LocalDate date = weekStart.plusDays(i);
            MealPlanDay day = week == null ? null : week.day(date).orElse(null);
            dayViews.add(
                    day == null
                            ? new MealPlanResponse.Day(date, false, "", List.of())
                            : new MealPlanResponse.Day(
                                    date,
                                    day.isOff(),
                                    day.getNote(),
                                    day.getItems().stream()
                                            .map(item -> {
                                                Dish dish = dishes.get(item.getDishId());
                                                return new MealPlanResponse.Item(
                                                        item.getSession(),
                                                        item.getMenuBlock(),
                                                        item.getDishId(),
                                                        dish == null ? null : dish.getName(),
                                                        dish == null ? null : dish.getDishType());
                                            })
                                            .toList()));
        }
        MealPlanEvaluator.Result result = evaluate(weekStart, week, groups, dishes);
        boolean manage = currentUser.hasPermission(Permission.MENU_MANAGE);
        boolean published = week != null && week.isPublished();
        return new MealPlanResponse(
                week == null ? null : week.getId(),
                weekStart,
                week == null ? NONE : week.getStatus().name(),
                week == null ? null : week.getPublishedAt(),
                week == null ? null : week.getPublishedBy(),
                manage && !published,
                manage && week != null && !published,
                week != null && !published && result.publishable(),
                blocks,
                dayViews,
                result.evaluations(),
                result.issues());
    }

    private MealPlanEvaluator.Result evaluate(LocalDate weekStart, MealPlanWeek week) {
        List<AgeGroupInfo> groups = evaluatedGroups(ageGroupService.getAgeGroups());
        Map<Long, Dish> dishes = dishRepository
                .findByIdIn(week.getDays().stream()
                        .flatMap(d -> d.getItems().stream())
                        .map(MealPlanItem::getDishId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        return evaluate(weekStart, week, groups, dishes);
    }

    private MealPlanEvaluator.Result evaluate(
            LocalDate weekStart, MealPlanWeek week, List<AgeGroupInfo> groups, Map<Long, Dish> dishes) {
        List<DayPlan> days = new ArrayList<>();
        for (int i = 0; i < SCHOOL_DAYS; i++) {
            LocalDate date = weekStart.plusDays(i);
            MealPlanDay day = week == null ? null : week.day(date).orElse(null);
            days.add(
                    day == null
                            ? new DayPlan(date, false, List.of())
                            : new DayPlan(
                                    date,
                                    day.isOff(),
                                    day.getItems().stream()
                                            .map(it -> new ItemPlan(it.getSession(), it.getMenuBlock(), it.getDishId()))
                                            .toList()));
        }
        Map<Long, List<AgeGroupMealConfig>> configs =
                configRepository
                        .findByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(weekStart.plusDays(SCHOOL_DAYS - 1))
                        .stream()
                        .collect(Collectors.groupingBy(AgeGroupMealConfig::getAgeGroupId));
        return MealPlanEvaluator.evaluate(
                days,
                groups,
                dishes.values().stream().collect(Collectors.toMap(Dish::getId, MealPlanServiceImpl::toDishInfo)),
                (ageGroupId, date) -> configs.getOrDefault(ageGroupId, List.of()).stream()
                        .filter(c -> !c.getEffectiveFrom().isAfter(date))
                        .findFirst()
                        .map(MealPlanServiceImpl::toNorm)
                        .orElse(null));
    }

    /** Nhóm tuổi có lớp trong năm học hiện hành; chưa có lớp nào thì kiểm mọi nhóm tuổi. */
    private List<AgeGroupInfo> evaluatedGroups(List<AgeGroupDetailResponse> ageGroups) {
        Set<Long> withClasses = classroomService.findActiveYearClassrooms().stream()
                .map(ClassroomInfoResponse::ageGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return ageGroups.stream()
                .filter(g -> withClasses.isEmpty() || withClasses.contains(g.id()))
                .filter(g -> g.mealSessions() != null && !g.mealSessions().isEmpty())
                .map(g -> new AgeGroupInfo(
                        g.id(), g.name(), MenuBlock.ofAgeGroup(g.ageToMonths()), List.copyOf(g.mealSessions())))
                .toList();
    }

    private static Map<MenuBlock, Set<MealSession>> servedSessions(Collection<AgeGroupDetailResponse> ageGroups) {
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

    private static DishInfo toDishInfo(Dish dish) {
        Map<Long, List<Portion>> byAgeGroup = new HashMap<>();
        for (DishIngredient ingredient : dish.getIngredients()) {
            Food food = ingredient.getFood();
            FoodInfo info = new FoodInfo(
                    food.getId(),
                    food.getKcal(),
                    food.getProtein(),
                    food.getLipid(),
                    food.getGlucid(),
                    food.getOrigin(),
                    food.getUnitPrice(),
                    food.getPurchaseUnitSize(),
                    food.getAllergens().stream().map(a -> a.getName()).sorted().toList());
            byAgeGroup
                    .computeIfAbsent(ingredient.getAgeGroupId(), id -> new ArrayList<>())
                    .add(new Portion(info, ingredient.getGramsPerChild()));
        }
        return new DishInfo(dish.getId(), dish.getName(), dish.getDishType(), byAgeGroup);
    }

    private static Norm toNorm(AgeGroupMealConfig c) {
        return new Norm(
                c.getMealPrice(),
                c.getKcalMin(),
                c.getKcalMax(),
                c.getProteinPctMin(),
                c.getProteinPctMax(),
                c.getLipidPctMin(),
                c.getLipidPctMax(),
                c.getGlucidPctMin(),
                c.getGlucidPctMax(),
                c.getAnimalProteinPctMin(),
                c.getAnimalProteinPctMax(),
                c.getAnimalLipidPctMin(),
                c.getAnimalLipidPctMax());
    }

    private static String blockLabel(MenuBlock block) {
        return block == MenuBlock.NURSERY ? "Nhà trẻ" : "Mẫu giáo";
    }
}
