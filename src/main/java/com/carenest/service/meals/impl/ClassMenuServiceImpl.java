package com.carenest.service.meals.impl;

import com.carenest.dto.meals.ClassMenuResponse;
import com.carenest.dto.meals.ClassMenuResponse.ClassMenuDay;
import com.carenest.dto.meals.ClassMenuResponse.ClassMenuDish;
import com.carenest.dto.meals.ClassMenuResponse.ClassMenuMeal;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.meals.ClassMenuService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.Allergen;
import com.carenest.utils.enums.MenuBlock;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassMenuServiceImpl implements ClassMenuService {
    private static final String LOG_PREFIX = "[ClassMenuServiceImpl]";
    private static final int SCHOOL_DAYS = 5;

    private final ClassroomAccessPolicy classroomAccessPolicy;
    private final ClassroomService classroomService;
    private final SchoolYearService schoolYearService;
    private final MealPlanWeekRepository weekRepository;
    private final DishRepository dishRepository;
    private final MealDemandCalculator demandCalculator;
    private final NutritionCatalogMapper catalogMapper;
    private final Clock clock;

    @Override
    public ClassMenuResponse getWeek(CurrentUser currentUser, Long classId, LocalDate weekStart) {
        String logPrefix = LOG_PREFIX + "|getWeek|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            // AUTH-03: giáo viên chỉ lớp đang được phân công
            classroomAccessPolicy.checkCanViewClassroom(currentUser, classId);
            ClassroomInfoResponse classroom = classroomService
                    .findClassroomInfo(classId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.CLASS_ACCESS_DENIED));
            LocalDate monday = (weekStart == null ? LocalDate.now(clock) : weekStart).with(DayOfWeek.MONDAY);
            MealPlanWeek week = weekRepository.findByWeekStart(monday).orElse(null);
            // NUT-08: tuần nháp hoặc chưa lập thì không trả món
            boolean published = week != null && week.isPublished();
            Map<Long, Dish> dishes = published ? dishesOf(week) : Map.of();
            MenuBlock block = demandCalculator.blockOfAgeGroup().get(classroom.ageGroupId());
            List<ClassMenuDay> days = IntStream.range(0, SCHOOL_DAYS)
                    .mapToObj(monday::plusDays)
                    .map(date -> day(date, classroom, published ? week : null, block, dishes))
                    .toList();
            log.info("{}|published={}|END", logPrefix, published);
            return new ClassMenuResponse(classId, monday, published, days);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Map<Long, Dish> dishesOf(MealPlanWeek week) {
        List<Long> ids = week.getDays().stream()
                .flatMap(day -> day.getItems().stream())
                .map(MealPlanItem::getDishId)
                .distinct()
                .toList();
        return ids.isEmpty()
                ? Map.of()
                : dishRepository.findByIdIn(ids).stream().collect(Collectors.toMap(Dish::getId, Function.identity()));
    }

    private ClassMenuDay day(
            LocalDate date,
            ClassroomInfoResponse classroom,
            MealPlanWeek week,
            MenuBlock block,
            Map<Long, Dish> dishes) {
        boolean inYear = !date.isBefore(classroom.yearStart()) && !date.isAfter(classroom.yearEnd());
        MealPlanDay planDay = week == null ? null : week.day(date).orElse(null);
        boolean dayOff = (planDay != null && planDay.isOff()) || !schoolYearService.isSchoolDay(date);
        if (planDay == null || !inYear || dayOff) {
            return new ClassMenuDay(date, inYear, dayOff, List.of());
        }
        List<ClassMenuMeal> meals = classroom.mealSessions().stream()
                .sorted()
                .map(session -> new ClassMenuMeal(
                        session,
                        planDay.getItems().stream()
                                .filter(item -> item.getSession() == session && item.getMenuBlock() == block)
                                .map(item -> dishes.get(item.getDishId()))
                                .filter(Objects::nonNull)
                                .map(this::dish)
                                .toList()))
                .filter(meal -> !meal.dishes().isEmpty())
                .toList();
        return new ClassMenuDay(date, true, false, meals);
    }

    private ClassMenuDish dish(Dish dish) {
        EnumSet<Allergen> allergens = dish.getIngredients().stream()
                .flatMap(ingredient -> ingredient.getFood().getAllergens().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Allergen.class)));
        return new ClassMenuDish(dish.getName(), catalogMapper.toAllergenResponses(allergens));
    }
}
