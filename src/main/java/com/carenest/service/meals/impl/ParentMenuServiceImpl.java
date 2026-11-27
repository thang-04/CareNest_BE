package com.carenest.service.meals.impl;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.meals.ParentMenuResponse;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuDay;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuDish;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuMeal;
import com.carenest.dto.meals.ParentMenuResponse.ParentMenuPhoto;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.MealPhoto;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.mapper.meals.NutritionCatalogMapper;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.MealPhotoRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.meals.MealPhotoService;
import com.carenest.service.meals.ParentMenuService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.Allergen;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FileCategory;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
public class ParentMenuServiceImpl implements ParentMenuService {
    private static final String LOG_PREFIX = "[ParentMenuServiceImpl]";
    private static final int SCHOOL_DAYS = 5;

    private final GuardianChildLinkService guardianChildLinkService;
    private final EnrollmentService enrollmentService;
    private final ChildService childService;
    private final ClassroomService classroomService;
    private final SchoolYearService schoolYearService;
    private final MealPlanWeekRepository weekRepository;
    private final MealPhotoRepository photoRepository;
    private final DishRepository dishRepository;
    private final MealDemandCalculator demandCalculator;
    private final NutritionCatalogMapper catalogMapper;
    private final FileStorage storageService;
    private final Clock clock;

    private record PhotoSlot(Long campusId, LocalDate date, MealSession session) {}

    private record Allergies(Set<Allergen> matched, List<String> unmatched) {}

    /** Những gì dựng nên một tuần của con; {@code showMeals} false ⇒ không món, không ảnh. */
    private record WeekView(
            MealPlanWeek week,
            boolean showMeals,
            Map<LocalDate, ClassroomInfoResponse> classByDate,
            Map<Long, MenuBlock> blocks,
            Map<Long, Dish> dishes,
            Map<PhotoSlot, MealPhoto> photos,
            Set<Allergen> childAllergens) {}

    @Override
    public ParentMenuResponse getWeek(CurrentUser currentUser, Long childId, LocalDate weekStart) {
        String logPrefix = LOG_PREFIX + "|getWeek|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkParent(currentUser, childId);
            LocalDate requested = weekStart == null ? LocalDate.now(clock) : weekStart;
            ParentMenuResponse response = week(childId, requested.with(DayOfWeek.MONDAY));
            log.info("{}|published={}|END", logPrefix, response.published());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FileUrlResponse getPhotoUrl(CurrentUser currentUser, Long childId, Long photoId) {
        String logPrefix = LOG_PREFIX + "|getPhotoUrl|userId=" + currentUser.userId() + "|childId=" + childId
                + "|photoId=" + photoId;
        log.info("{}|START", logPrefix);
        try {
            MealPhoto photo = visiblePhoto(currentUser, childId, photoId);
            var url = storageService.presignGet(FileCategory.MEAL, photo.getStorageKey(), photo.getContentType(), null);
            FileUrlResponse response = new FileUrlResponse(url.url(), url.expiresAt());
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MealPhotoService.Content getPhotoContent(CurrentUser currentUser, Long childId, Long photoId) {
        String logPrefix = LOG_PREFIX + "|getPhotoContent|userId=" + currentUser.userId() + "|childId=" + childId
                + "|photoId=" + photoId;
        log.info("{}|START", logPrefix);
        try {
            MealPhoto photo = visiblePhoto(currentUser, childId, photoId);
            MealPhotoService.Content content = new MealPhotoService.Content(
                    storageService.load(FileCategory.MEAL, photo.getStorageKey()), photo.getContentType());
            log.info("{}|END", logPrefix);
            return content;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Tuần của con đã qua kiểm quyền; API tuần và API ảnh dùng chung để cùng một luật hiển thị. */
    private ParentMenuResponse week(Long childId, LocalDate monday) {
        LocalDate today = LocalDate.now(clock);
        List<LocalDate> dates =
                IntStream.range(0, SCHOOL_DAYS).mapToObj(monday::plusDays).toList();
        MealPlanWeek week = weekRepository.findByWeekStart(monday).orElse(null);
        boolean published = week != null && week.isPublished();
        boolean registered =
                !childService.findChildIdsWithoutMeals(Set.of(childId)).contains(childId);
        // PAR-14: không báo ăn (CHD-13, cờ hiện tại) ⇒ trống từ tuần hiện tại, tuần đã qua vẫn xem
        boolean hidden = !registered && !monday.isBefore(today.with(DayOfWeek.MONDAY));
        // CHD-13: trẻ không báo ăn không dùng khai báo dị ứng, kể cả khi xem tuần đã qua
        Allergies allergies = published && registered ? allergiesOf(childId) : new Allergies(Set.of(), List.of());
        WeekView view = load(childId, dates, week, published && !hidden, allergies.matched());
        return new ParentMenuResponse(
                childId,
                monday,
                published,
                hidden,
                allergies.unmatched(),
                dates.stream().map(date -> day(date, view)).toList());
    }

    // PAR-14: kiểm quyền trước khi tìm ảnh; ảnh không hiện trong tuần của con báo như ảnh không tồn tại
    private MealPhoto visiblePhoto(CurrentUser currentUser, Long childId, Long photoId) {
        checkParent(currentUser, childId);
        MealPhoto photo =
                photoRepository.findById(photoId).orElseThrow(() -> GlobalException.of(ApiCode.MEAL_PHOTO_NOT_FOUND));
        boolean shown = week(childId, photo.getPhotoDate().with(DayOfWeek.MONDAY)).days().stream()
                .flatMap(day -> day.meals().stream())
                .anyMatch(meal -> meal.photo() != null && meal.photo().photoId().equals(photo.getId()));
        if (!shown) {
            throw GlobalException.of(ApiCode.MEAL_PHOTO_NOT_FOUND);
        }
        return photo;
    }

    // AUTH-04, CHD-04: chỉ phụ huynh đã liên kết, con còn học
    private void checkParent(CurrentUser user, Long childId) {
        if (!user.hasRole(CareNestRole.PARENT)
                || !guardianChildLinkService.isGuardianOf(user.userId(), childId)
                || !enrollmentService
                        .findChildIdsLeftSchool(Set.of(childId), LocalDate.now(clock))
                        .isEmpty()) {
            throw GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN);
        }
    }

    private WeekView load(
            Long childId, List<LocalDate> dates, MealPlanWeek week, boolean showMeals, Set<Allergen> childAllergens) {
        Map<LocalDate, ClassroomInfoResponse> classByDate = classByDate(childId, dates);
        if (!showMeals) {
            return new WeekView(week, false, classByDate, Map.of(), Map.of(), Map.of(), childAllergens);
        }
        Set<Long> dishIds = week.getDays().stream()
                .flatMap(day -> day.getItems().stream())
                .map(MealPlanItem::getDishId)
                .collect(Collectors.toSet());
        // Nạp kèm nguyên liệu, thực phẩm: chất gây dị ứng của món gộp từ nguyên liệu
        Map<Long, Dish> dishes = dishIds.isEmpty()
                ? Map.of()
                : dishRepository.findByIdIn(dishIds).stream()
                        .collect(Collectors.toMap(Dish::getId, Function.identity()));
        Map<PhotoSlot, MealPhoto> photos = classByDate.values().stream()
                .map(ClassroomInfoResponse::campusId)
                .distinct()
                .flatMap(campusId -> photoRepository
                        .findByCampusIdAndPhotoDateBetweenOrderByPhotoDateAscSessionAsc(
                                campusId, dates.getFirst(), dates.getLast())
                        .stream())
                .collect(Collectors.toMap(
                        p -> new PhotoSlot(p.getCampusId(), p.getPhotoDate(), p.getSession()), Function.identity()));
        return new WeekView(
                week, true, classByDate, demandCalculator.blockOfAgeGroup(), dishes, photos, childAllergens);
    }

    /** Lớp của con theo từng ngày; ngày không có lớp thì không có khóa. */
    private Map<LocalDate, ClassroomInfoResponse> classByDate(Long childId, List<LocalDate> dates) {
        List<EnrollmentService.ChildClassStay> stays =
                enrollmentService.findChildStays(childId, dates.getFirst(), dates.getLast());
        Map<Long, ClassroomInfoResponse> classes = new HashMap<>();
        stays.forEach(stay ->
                classroomService.findClassroomInfo(stay.classroomId()).ifPresent(info -> classes.put(info.id(), info)));
        Map<LocalDate, ClassroomInfoResponse> result = new LinkedHashMap<>();
        for (LocalDate date : dates) {
            // Chuyển lớp trùng ngày thì lấy lớp bắt đầu sau; xếp lớp chưa đóng không kéo quá năm học của lớp
            Optional<ClassroomInfoResponse> classroom = stays.stream()
                    .filter(stay -> !date.isBefore(stay.from()) && !date.isAfter(stay.to()))
                    .sorted(Comparator.comparing(EnrollmentService.ChildClassStay::from)
                            .reversed())
                    .map(stay -> classes.get(stay.classroomId()))
                    .filter(Objects::nonNull)
                    .filter(info -> !date.isBefore(info.yearStart()) && !date.isAfter(info.yearEnd()))
                    .findFirst();
            classroom.ifPresent(info -> result.put(date, info));
        }
        return result;
    }

    private ParentMenuDay day(LocalDate date, WeekView view) {
        MealPlanDay planDay = view.week() == null ? null : view.week().day(date).orElse(null);
        // Ngày nghỉ trong kế hoạch nháp chưa phải thông tin đã công bố
        boolean offInPlan = view.week() != null && view.week().isPublished() && planDay != null && planDay.isOff();
        boolean dayOff = offInPlan || !schoolYearService.isSchoolDay(date);
        ClassroomInfoResponse classroom = view.classByDate().get(date);
        List<ParentMenuMeal> meals = classroom == null || dayOff || !view.showMeals()
                ? List.of()
                : classroom.mealSessions().stream()
                        .sorted()
                        .map(session -> meal(date, session, classroom, planDay, view))
                        .filter(meal -> !meal.dishes().isEmpty() || meal.photo() != null)
                        .toList();
        return new ParentMenuDay(date, classroom != null, dayOff, meals);
    }

    private ParentMenuMeal meal(
            LocalDate date, MealSession session, ClassroomInfoResponse classroom, MealPlanDay planDay, WeekView view) {
        MenuBlock block = view.blocks().get(classroom.ageGroupId());
        List<ParentMenuDish> dishes = planDay == null
                ? List.of()
                : planDay.getItems().stream()
                        .filter(item -> item.getSession() == session && item.getMenuBlock() == block)
                        .map(item -> view.dishes().get(item.getDishId()))
                        .filter(Objects::nonNull)
                        .map(dish -> dish(dish, view.childAllergens()))
                        .toList();
        MealPhoto photo = view.photos().get(new PhotoSlot(classroom.campusId(), date, session));
        return new ParentMenuMeal(
                session, dishes, photo == null ? null : new ParentMenuPhoto(photo.getId(), photo.getUploadedAt()));
    }

    private ParentMenuDish dish(Dish dish, Set<Allergen> childAllergens) {
        Collection<Allergen> allergens = dish.getIngredients().stream()
                .flatMap(ingredient -> ingredient.getFood().getAllergens().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Allergen.class)));
        return new ParentMenuDish(
                dish.getName(),
                catalogMapper.toAllergenResponses(allergens),
                catalogMapper.toAllergenResponses(
                        allergens.stream().filter(childAllergens::contains).toList()));
    }

    /** NUT-23: đối chiếu chữ con đã khai với danh mục lúc đọc; chữ không khớp hiện lại, mỗi chữ một lần. */
    private Allergies allergiesOf(Long childId) {
        Set<Allergen> matched = EnumSet.noneOf(Allergen.class);
        Map<String, String> unmatched = new LinkedHashMap<>();
        for (String name : childService.findAllergyNames(Set.of(childId)).getOrDefault(childId, List.of())) {
            KitchenAllergyWarnings.match(name).ifPresentOrElse(matched::add, () -> {
                String label = KitchenAllergyWarnings.clean(name);
                if (!label.isEmpty()) {
                    unmatched.putIfAbsent(label.toLowerCase(Locale.ROOT), label);
                }
            });
        }
        return new Allergies(matched, List.copyOf(unmatched.values()));
    }
}
