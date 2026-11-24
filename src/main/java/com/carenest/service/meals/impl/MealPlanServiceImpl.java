package com.carenest.service.meals.impl;

import com.carenest.dto.attendance.DayInfoResponse;
import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.meals.MealPlanAmendRequest;
import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MealPlanRevisionResponse;
import com.carenest.dto.meals.MealPlanSummaryResponse;
import com.carenest.dto.meals.MenuStatisticsRequest;
import com.carenest.dto.meals.MenuStatisticsResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanDayRevision;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.repository.meals.MealPlanDayRevisionRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.meals.FoodPriceService;
import com.carenest.service.meals.FoodPriceService.FoodPriceBook;
import com.carenest.service.meals.MealPlanService;
import com.carenest.service.meals.StockLedgerService;
import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DayPlan;
import com.carenest.service.meals.impl.MealPlanEvaluator.ItemPlan;
import com.carenest.service.meals.impl.MealPlanEvaluator.Norm;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.CampusService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.Permission;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
    private static final int MIN_REASON_LENGTH = 10;

    private final MealPlanWeekRepository weekRepository;
    private final DishRepository dishRepository;
    private final MealCountRepository mealCountRepository;
    private final StockLedgerService stockLedgerService;
    private final AgeGroupService ageGroupService;
    private final ClassroomService classroomService;
    private final MealDemandCalculator demandCalculator;
    private final DryStockOutlook dryStockOutlook;
    private final FoodPriceService foodPriceService;
    private final CampusService campusService;
    private final MealPlanEvaluationSupport evaluationSupport;
    private final EnrollmentService enrollmentService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final NotificationService notificationService;
    private final MealPlanDayRevisionRepository revisionRepository;
    private final AttendanceService attendanceService;
    private final StaffAssignmentService staffAssignmentService;
    private final AccountService accountService;
    private final Clock clock;

    private static final String PRICE_CATALOG = "CATALOG";
    private static final String PRICE_LOT = "LOT";
    private static final String PRICE_MIXED = "MIXED";

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
            checkWeekNotPast(weekStart);
            MealPlanWeek week = weekRepository
                    .findForUpdateByWeekStart(weekStart)
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
            checkWeekNotPast(weekStart);
            MealPlanWeek week = weekRepository
                    .findForUpdateByWeekStart(weekStart)
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
            syncConfirmedDays(weekStart, weekStart.plusDays(SCHOOL_DAYS - 1));
            notifyParents(weekStart);
            MealPlanResponse response = toResponse(currentUser, weekStart, week);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public MealPlanResponse amendMealPlanDay(
            CurrentUser currentUser, LocalDate weekStart, LocalDate date, MealPlanAmendRequest request) {
        String logPrefix = LOG_PREFIX + "|amendMealPlanDay|userId=" + currentUser.userId() + "|weekStart=" + weekStart
                + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser);
            checkMonday(weekStart);
            checkInWeek(weekStart, date);
            String reason = request.reason() == null ? "" : request.reason().trim();
            if (reason.length() < MIN_REASON_LENGTH) {
                throw GlobalException.of(ApiCode.MEAL_PLAN_REASON_REQUIRED);
            }
            MealPlanWeek week = weekRepository
                    .findForUpdateByWeekStart(weekStart)
                    .filter(MealPlanWeek::isPublished)
                    .orElseThrow(() -> GlobalException.of(ApiCode.MEAL_PLAN_NOT_PUBLISHED));
            MealPlanDay before = week.day(date).orElse(MealPlanDay.of(date, false, "", List.of()));
            // P-39: chỉ ngày chưa tới hoặc hôm nay trước giờ chốt; ngày nghỉ thuộc lịch năm học, không đổi ở đây
            if (before.isOff() || !isAmendable(date)) {
                throw GlobalException.of(ApiCode.MEAL_PLAN_DAY_LOCKED, dayLabel(date));
            }
            MealPlanDay wanted = toDays(
                            weekStart,
                            new MealPlanRequest(List.of(
                                    new MealPlanRequest.DayInput(date, false, request.note(), request.items()))),
                            false)
                    .stream()
                    .filter(d -> d.getMenuDate().equals(date))
                    .findFirst()
                    .orElseThrow();
            if (sameContent(before, wanted)) {
                throw GlobalException.of(ApiCode.MEAL_PLAN_AMEND_NO_CHANGE, dayLabel(date));
            }
            List<MealPlanDayRevision.Item> beforeItems = revisionItems(before.getItems());
            week.amendPublishedDay(date, wanted.getNote(), wanted.getItems(), currentUser.userId());
            weekRepository.saveAndFlush(week);
            MealPlanDay after = week.day(date).orElseThrow();
            // Cân đối chỉ cảnh báo khi sửa ngoại lệ (user chốt 18/11); giữ lại cảnh báo lúc sửa để tra cứu
            List<MealPlanDayRevision.Issue> issues = evaluate(weekStart, week).issues().stream()
                    .filter(i -> date.equals(i.date()))
                    .map(i -> new MealPlanDayRevision.Issue(i.severity(), i.code(), i.message()))
                    .toList();
            revisionRepository.save(MealPlanDayRevision.of(
                    week.getId(),
                    date,
                    before.getNote(),
                    beforeItems,
                    after.getNote(),
                    revisionItems(after.getItems()),
                    reason,
                    issues,
                    currentUser.userId(),
                    clock.instant()));
            syncConfirmedDays(date, date);
            notifyMenuChanged(date, reason);
            MealPlanResponse response = toResponse(currentUser, weekStart, week);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MealPlanRevisionResponse> getMealPlanRevisions(CurrentUser currentUser, LocalDate weekStart) {
        String logPrefix =
                LOG_PREFIX + "|getMealPlanRevisions|userId=" + currentUser.userId() + "|weekStart=" + weekStart;
        log.info("{}|START", logPrefix);
        try {
            checkMonday(weekStart);
            List<MealPlanDayRevision> revisions = weekRepository
                    .findByWeekStart(weekStart)
                    .map(w -> revisionRepository.findByWeekIdOrderByChangedAtDescIdDesc(w.getId()))
                    .orElse(List.of());
            Map<Long, String> names = accountService.findFullNames(
                    revisions.stream().map(MealPlanDayRevision::getChangedBy).collect(Collectors.toSet()));
            List<MealPlanRevisionResponse> response = revisions.stream()
                    .map(r -> new MealPlanRevisionResponse(
                            r.getId(),
                            r.getMenuDate(),
                            dayContent(r.getBeforeNote(), r.getBeforeItems()),
                            dayContent(r.getAfterNote(), r.getAfterItems()),
                            r.getReason(),
                            r.getIssues().stream()
                                    .map(i -> new MealPlanRevisionResponse.RevisionIssue(
                                            i.severity(), i.code(), i.message()))
                                    .toList(),
                            r.getChangedBy(),
                            names.get(r.getChangedBy()),
                            r.getChangedAt()))
                    .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static MealPlanRevisionResponse.RevisionDay dayContent(String note, List<MealPlanDayRevision.Item> items) {
        return new MealPlanRevisionResponse.RevisionDay(
                note,
                items.stream()
                        .map(i -> new MealPlanRevisionResponse.RevisionItem(
                                i.session(), i.block(), i.dishId(), i.dishName()))
                        .toList());
    }

    private static void checkInWeek(LocalDate weekStart, LocalDate date) {
        long offset = date.toEpochDay() - weekStart.toEpochDay();
        if (offset < 0 || offset >= SCHOOL_DAYS) {
            throw GlobalException.of(ApiCode.MEAL_PLAN_DATE_INVALID, date);
        }
    }

    /** P-39: ngày học chưa qua giờ chốt (ngày tới hoặc hôm nay trước giờ chốt) — cùng nguồn giờ chốt với sổ báo ăn. */
    private boolean isAmendable(LocalDate date) {
        if (date.isBefore(LocalDate.now(clock))) {
            return false;
        }
        DayInfoResponse info = attendanceService.getActiveYearDayInfo(date);
        return info != null && info.schoolDay() && !info.baseLocked();
    }

    private static boolean sameContent(MealPlanDay a, MealPlanDay b) {
        return a.getNote().equals(b.getNote()) && slots(a).equals(slots(b));
    }

    private static List<String> slots(MealPlanDay day) {
        return day.getItems().stream()
                .map(i -> i.getSession() + "|" + i.getMenuBlock() + "|" + i.getDishId())
                .toList();
    }

    private List<MealPlanDayRevision.Item> revisionItems(List<MealPlanItem> items) {
        Map<Long, String> names =
                dishRepository
                        .findAllById(items.stream().map(MealPlanItem::getDishId).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Dish::getId, Dish::getName));
        return items.stream()
                .map(i -> new MealPlanDayRevision.Item(
                        i.getSession(), i.getMenuBlock(), i.getDishId(), names.get(i.getDishId())))
                .toList();
    }

    /** P-39: sửa ngoại lệ ⇒ báo phụ huynh của trẻ đang học và bếp hai điểm trường. */
    private void notifyMenuChanged(LocalDate date, String reason) {
        LocalDate today = LocalDate.now(clock);
        Set<Long> recipients = new LinkedHashSet<>(parentIds(today));
        classroomService.findActiveYearClassrooms().stream()
                .map(ClassroomInfoResponse::campusId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(campusId -> recipients.addAll(staffAssignmentService.findCampusKitchenIds(campusId, today)));
        if (recipients.isEmpty()) {
            return;
        }
        notificationService.notifyEvent(
                recipients,
                NotificationType.MENU_CHANGED,
                Map.of("ngay", DateTimeFormatter.ofPattern("dd/MM").format(date), "lyDo", reason),
                null);
    }

    private List<Long> parentIds(LocalDate today) {
        Set<Long> childIds = new LinkedHashSet<>();
        for (var cls : classroomService.findActiveYearClassrooms()) {
            enrollmentService.findChildrenInClassroom(cls.id(), today).stream()
                    .map(ClassChildResponse::id)
                    .forEach(childIds::add);
        }
        if (childIds.isEmpty()) {
            return List.of();
        }
        return guardianChildLinkService.findLinksOfChildren(childIds).stream()
                .map(GuardianLinkResponse::userId)
                .toList();
    }

    /** NUT-08: công bố thực đơn tuần ⇒ báo phụ huynh của trẻ đang học (HT tắt được — PAR-09). */
    private void notifyParents(LocalDate weekStart) {
        List<Long> parents = parentIds(LocalDate.now(clock));
        if (parents.isEmpty()) {
            return;
        }
        DateTimeFormatter dm = DateTimeFormatter.ofPattern("dd/MM");
        notificationService.notifyEvent(
                parents,
                NotificationType.MENU_PUBLISHED,
                Map.of("tuan", dm.format(weekStart) + " – " + dm.format(weekStart.plusDays(SCHOOL_DAYS - 1))),
                null);
    }

    // P-38: suất đã chốt trước khi công bố (hiếm) hoặc trước khi sửa ngoại lệ ⇒ trừ kho lại theo thực đơn mới
    private void syncConfirmedDays(LocalDate from, LocalDate to) {
        Set<String> done = new LinkedHashSet<>();
        for (MealCount count : mealCountRepository.findByDateBetween(from, to)) {
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

    // NUT-08: tuần đã ăn xong (qua thứ Sáu) không lưu hay công bố nữa; sửa ngày đã qua đi đường sửa ngoại lệ
    private boolean isWeekPast(LocalDate weekStart) {
        return weekStart.plusDays(SCHOOL_DAYS - 1L).isBefore(LocalDate.now(clock));
    }

    private void checkWeekNotPast(LocalDate weekStart) {
        if (isWeekPast(weekStart)) {
            throw GlobalException.of(ApiCode.MEAL_PLAN_WEEK_PAST, weekStart);
        }
    }

    private static void checkMonday(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw GlobalException.of(ApiCode.MEAL_PLAN_WEEK_INVALID);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MenuStatisticsResponse getMenuStatistics(CurrentUser currentUser, MenuStatisticsRequest request) {
        String logPrefix = LOG_PREFIX + "|getMenuStatistics|userId=" + currentUser.userId() + "|ageGroupId="
                + request.ageGroupId();
        log.info("{}|START", logPrefix);
        try {
            LocalDate date = request.date() == null ? LocalDate.now(clock) : request.date();
            if (request.ageGroupId() == null) {
                List<MenuStatisticsResponse> groups = ageGroupService.getAgeGroups().stream()
                        .map(group -> {
                            MenuBlock block = MenuBlock.ofAgeGroup(group.ageToMonths());
                            List<MenuStatisticsRequest.Item> groupItems = request.items().stream()
                                    .filter(item -> (item.block() == null || item.block() == block)
                                            && group.mealSessions() != null
                                            && group.mealSessions().contains(item.session()))
                                    .toList();
                            if (groupItems.isEmpty()) return null;
                            return getMenuStatistics(
                                    currentUser,
                                    new MenuStatisticsRequest(group.id(), date, request.campusId(), null, groupItems));
                        })
                        .filter(Objects::nonNull)
                        .filter(group -> !group.dishes().isEmpty())
                        .toList();
                int children = groups.stream()
                        .mapToInt(MenuStatisticsResponse::children)
                        .sum();
                BigDecimal totalCost =
                        groups.stream().map(MenuStatisticsResponse::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal freshCost =
                        groups.stream().map(MenuStatisticsResponse::freshCost).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal dryCost =
                        groups.stream().map(MenuStatisticsResponse::dryCost).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal mealPrice = groups.stream()
                        .map(MenuStatisticsResponse::mealPrice)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(null);
                BigDecimal foodBudget = groups.stream()
                        .map(MenuStatisticsResponse::foodBudget)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(null);
                String childrenSource = groups.stream()
                                        .map(MenuStatisticsResponse::childrenSource)
                                        .distinct()
                                        .count()
                                == 1
                        ? groups.getFirst().childrenSource()
                        : "MIXED";
                MenuStatisticsResponse response = new MenuStatisticsResponse(
                        null,
                        "Tất cả nhóm tuổi",
                        date,
                        children,
                        childrenSource,
                        List.of(),
                        List.of(),
                        List.of(),
                        totalCost,
                        freshCost,
                        dryCost,
                        List.of(),
                        children == 0
                                ? BigDecimal.ZERO
                                : totalCost.divide(BigDecimal.valueOf(children), 0, RoundingMode.HALF_UP),
                        mealPrice,
                        null,
                        foodBudget,
                        null,
                        groups);
                log.info("{}|END", logPrefix);
                return response;
            }
            AgeGroupDetailResponse group = ageGroupService.getAgeGroups().stream()
                    .filter(g -> g.id().equals(request.ageGroupId()))
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND));
            List<MealSession> sessions = group.mealSessions() == null ? List.of() : List.copyOf(group.mealSessions());
            AgeGroupInfo info =
                    new AgeGroupInfo(group.id(), group.name(), MenuBlock.ofAgeGroup(group.ageToMonths()), sessions);
            Map<Long, Dish> dishes = dishRepository
                    .findByIdIn(request.items().stream()
                            .map(MenuStatisticsRequest.Item::dishId)
                            .collect(Collectors.toSet()))
                    .stream()
                    .collect(Collectors.toMap(Dish::getId, Function.identity()));
            // Chỉ bữa nhóm tuổi được phục vụ, mỗi món một lần trong bữa (như lưới kế hoạch tuần)
            List<ItemPlan> items = request.items().stream()
                    .filter(i -> dishes.containsKey(i.dishId())
                            && sessions.contains(i.session())
                            && (i.block() == null || i.block() == info.block()))
                    .map(i -> new ItemPlan(i.session(), info.block(), i.dishId()))
                    .distinct()
                    .toList();
            Norm norm = evaluationSupport.normsUntil(date).apply(group.id(), date);
            FoodPriceBook book = priceBook(dishes.values());
            MealPlanResponse.Evaluation evaluation = MealPlanEvaluator.evaluate(
                            List.of(new DayPlan(date, false, items)),
                            List.of(info),
                            MealPlanEvaluationSupport.dishInfos(dishes.values()),
                            (ageGroupId, day) -> norm,
                            book::price)
                    .evaluations()
                    .getFirst();
            Headcount headcount = headcountOf(request, group.id(), date);
            List<MenuStatisticsResponse.Ingredient> ingredients =
                    ingredientsOf(items, dishes, group.id(), headcount.count(), book, date, request.campusId());
            List<MenuStatisticsResponse.DishIngredientLine> dishIngredients =
                    dishIngredientsOf(items, dishes, group.id(), book, date);
            BigDecimal totalCost = ingredients.stream()
                    .map(MenuStatisticsResponse.Ingredient::cost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // NUT-10: hàng tươi phải đặt mua, hàng khô lấy từ kho — tách tổng để màn "Định lượng & đi chợ" chia hai
            // bảng
            BigDecimal freshCost = costOf(ingredients, FoodStorage.FRESH);
            BigDecimal dryCost = costOf(ingredients, FoodStorage.DRY);
            List<MenuStatisticsResponse.SupplierCost> supplierCosts = supplierCostsOf(ingredients);
            List<MenuStatisticsResponse.Dish> dishViews = items.stream()
                    .map(i -> {
                        Dish dish = dishes.get(i.dishId());
                        return new MenuStatisticsResponse.Dish(
                                i.session(), dish.getId(), dish.getName(), dish.getDishType());
                    })
                    .toList();
            MenuStatisticsResponse response = new MenuStatisticsResponse(
                    group.id(),
                    group.name(),
                    date,
                    headcount.count(),
                    headcount.source(),
                    dishViews,
                    ingredients,
                    dishIngredients,
                    totalCost,
                    freshCost,
                    dryCost,
                    supplierCosts,
                    evaluation.cost(),
                    evaluation.mealPrice(),
                    evaluation.serviceFee(),
                    evaluation.foodBudget(),
                    evaluation,
                    List.of());
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private record Headcount(int count, String source) {}

    private static BigDecimal costOf(List<MenuStatisticsResponse.Ingredient> ingredients, FoodStorage storage) {
        return ingredients.stream()
                .filter(i -> i.storage() == storage)
                .map(MenuStatisticsResponse.Ingredient::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Danh sách đặt hàng theo NCC: cộng tiền từng NCC để màn nhóm theo NCC không tự tính ở client
    private static List<MenuStatisticsResponse.SupplierCost> supplierCostsOf(
            List<MenuStatisticsResponse.Ingredient> ingredients) {
        Map<Long, MenuStatisticsResponse.SupplierCost> bySupplier = new LinkedHashMap<>();
        for (MenuStatisticsResponse.Ingredient i : ingredients) {
            bySupplier.merge(
                    i.supplierId(),
                    new MenuStatisticsResponse.SupplierCost(
                            i.supplierId(), i.supplierName(), i.contractStatus(), i.cost()),
                    (a, b) -> new MenuStatisticsResponse.SupplierCost(
                            a.supplierId(),
                            a.supplierName(),
                            a.contractStatus(),
                            a.cost().add(b.cost())));
        }
        return List.copyOf(bySupplier.values());
    }

    // N trẻ: người xem nhập > suất đã xác nhận của điểm trường–ngày (NUT-03) > sĩ số đang học của nhóm tuổi
    private Headcount headcountOf(MenuStatisticsRequest request, Long ageGroupId, LocalDate date) {
        if (request.children() != null) {
            return new Headcount(request.children(), "INPUT");
        }
        if (request.campusId() != null) {
            List<MealCount> counts = mealCountRepository.findByCampusIdAndDate(request.campusId(), date);
            // Cơ sở–ngày đã chốt suất thì dùng số chốt, kể cả 0 (cả nhóm vắng) — không quay về sĩ số
            if (counts.stream().anyMatch(MealCount::isConfirmed)) {
                int confirmed = demandCalculator.confirmedServings(counts).stream()
                        .filter(s -> s.ageGroupId().equals(ageGroupId))
                        .mapToInt(MealDemandCalculator.Servings::portions)
                        .max()
                        .orElse(0);
                return new Headcount(confirmed, "CONFIRMED");
            }
        }
        List<Long> classIds = classroomService.findActiveYearClassrooms().stream()
                .filter(c -> ageGroupId.equals(c.ageGroupId()))
                .filter(c -> request.campusId() == null || request.campusId().equals(c.campusId()))
                .map(ClassroomInfoResponse::id)
                .toList();
        long enrolled = classIds.isEmpty()
                ? 0
                : classroomService.countMealRegisteredChildren(classIds, date).values().stream()
                        .mapToLong(Long::longValue)
                        .sum();
        return new Headcount((int) enrolled, "ENROLLED");
    }

    private FoodPriceBook priceBook(Collection<Dish> dishes) {
        return foodPriceService.book(dishes.stream()
                .flatMap(d -> d.getIngredients().stream())
                .map(i -> i.getFood().getId())
                .collect(Collectors.toSet()));
    }

    // NUT-07: lượng ăn = gam/trẻ × N; lượng mua = ăn ÷ (1 − thải bỏ%); tiền = lượng mua theo đơn vị mua × đơn giá
    // NUT-27: giá và NCC tại ngày thống kê; hàng khô có điểm trường thì giá lô FEFO, phần thiếu kho theo giá danh mục
    private List<MenuStatisticsResponse.Ingredient> ingredientsOf(
            List<ItemPlan> items,
            Map<Long, Dish> dishes,
            Long ageGroupId,
            int children,
            FoodPriceBook book,
            LocalDate date,
            Long campusId) {
        Map<Long, BigDecimal> grams = new LinkedHashMap<>();
        Map<Long, Food> foods = new HashMap<>();
        for (ItemPlan item : items) {
            for (DishIngredient ingredient : dishes.get(item.dishId()).getIngredients()) {
                if (!ageGroupId.equals(ingredient.getAgeGroupId())) {
                    continue;
                }
                foods.put(ingredient.getFood().getId(), ingredient.getFood());
                grams.merge(ingredient.getFood().getId(), ingredient.getGramsPerChild(), BigDecimal::add);
            }
        }
        BigDecimal n = BigDecimal.valueOf(children);
        Map<Long, BigDecimal> dryNeed = new HashMap<>();
        if (campusId != null) {
            grams.forEach((foodId, perChild) -> {
                Food food = foods.get(foodId);
                if (food.getStorage() == FoodStorage.DRY) {
                    dryNeed.put(foodId, MealDemandCalculator.purchased(perChild.multiply(n), food.getWastePct()));
                }
            });
        }
        Map<Long, DryStockOutlook.LotTake> lots =
                dryNeed.isEmpty() ? Map.of() : dryStockOutlook.lotPricing(campusId, dryNeed, date);
        List<MenuStatisticsResponse.Ingredient> rows = new ArrayList<>();
        grams.forEach((foodId, perChild) -> {
            Food food = foods.get(foodId);
            FoodPriceBook.PriceAt price = book.at(foodId, date);
            BigDecimal eaten = perChild.multiply(n);
            BigDecimal purchase = MealDemandCalculator.purchased(eaten, food.getWastePct());
            BigDecimal size = food.getPurchaseUnitSize();
            boolean priced = size != null && size.signum() > 0;
            // NUT-12: số mua theo ĐVT làm tròn lên nguyên đơn vị khi đếm được; tiền ăn vẫn theo lượng thực dùng
            BigDecimal qty =
                    priced ? PurchaseUnits.roundUp(purchase, food).divide(size, 3, RoundingMode.HALF_UP) : null;
            DryStockOutlook.LotTake lot = lots.get(foodId);
            BigDecimal cost = BigDecimal.ZERO;
            String priceSource = PRICE_CATALOG;
            BigDecimal stockShort = null;
            if (priced && lot != null) {
                BigDecimal value = lot.lotValue();
                if (price.price() != null) {
                    value = value.add(lot.catalogQty().multiply(price.price()));
                }
                cost = value.divide(size, 0, RoundingMode.HALF_UP);
                priceSource = lot.lotQty().signum() == 0
                        ? PRICE_CATALOG
                        : lot.catalogQty().signum() == 0 ? PRICE_LOT : PRICE_MIXED;
                stockShort = lot.shortQty().setScale(2, RoundingMode.HALF_UP);
            } else if (priced && price.price() != null) {
                cost = purchase.multiply(price.price()).divide(size, 0, RoundingMode.HALF_UP);
            }
            rows.add(new MenuStatisticsResponse.Ingredient(
                    foodId,
                    food.getName(),
                    food.getUnit(),
                    perChild,
                    eaten.setScale(2, RoundingMode.HALF_UP),
                    food.getWastePct(),
                    purchase.setScale(2, RoundingMode.HALF_UP),
                    food.getPurchaseUnit(),
                    size,
                    qty,
                    price.price(),
                    cost,
                    food.getStorage(),
                    price.supplierId(),
                    price.supplierName(),
                    price.contractStatus(),
                    priceSource,
                    stockShort));
        });
        rows.sort(Comparator.comparing(MenuStatisticsResponse.Ingredient::foodName));
        return rows;
    }

    // Công thức từng món cho một trẻ: cùng công thức lượng mua và tiền với ingredientsOf nhưng không × N, không gộp
    private static List<MenuStatisticsResponse.DishIngredientLine> dishIngredientsOf(
            List<ItemPlan> items, Map<Long, Dish> dishes, Long ageGroupId, FoodPriceBook book, LocalDate date) {
        List<MenuStatisticsResponse.DishIngredientLine> lines = new ArrayList<>();
        for (ItemPlan item : items) {
            Dish dish = dishes.get(item.dishId());
            for (DishIngredient ingredient : dish.getIngredients()) {
                if (!ageGroupId.equals(ingredient.getAgeGroupId())) {
                    continue;
                }
                Food food = ingredient.getFood();
                BigDecimal purchase = MealDemandCalculator.purchased(ingredient.getGramsPerChild(), food.getWastePct());
                BigDecimal size = food.getPurchaseUnitSize();
                BigDecimal unitPrice = book.price(food.getId(), date);
                boolean priced = size != null && size.signum() > 0 && unitPrice != null;
                BigDecimal cost =
                        priced ? purchase.multiply(unitPrice).divide(size, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                lines.add(new MenuStatisticsResponse.DishIngredientLine(
                        item.session(),
                        dish.getId(),
                        dish.getName(),
                        food.getId(),
                        food.getName(),
                        ingredient.getGramsPerChild(),
                        food.getWastePct(),
                        purchase.setScale(2, RoundingMode.HALF_UP),
                        food.getPurchaseUnit(),
                        unitPrice,
                        cost));
            }
        }
        return lines;
    }

    private List<MealPlanDay> toDays(LocalDate weekStart, MealPlanRequest request) {
        return toDays(weekStart, request, true);
    }

    private List<MealPlanDay> toDays(LocalDate weekStart, MealPlanRequest request, boolean planning) {
        Map<LocalDate, MealPlanRequest.DayInput> byDate = new LinkedHashMap<>();
        for (MealPlanRequest.DayInput day : request.days()) {
            long offset = day.date().toEpochDay() - weekStart.toEpochDay();
            if (offset < 0 || offset >= SCHOOL_DAYS || byDate.putIfAbsent(day.date(), day) != null) {
                throw GlobalException.of(ApiCode.MEAL_PLAN_DATE_INVALID, day.date());
            }
        }
        Map<MenuBlock, Set<MealSession>> served = MealPlanEvaluationSupport.servedSessions(
                planning ? evaluationSupport.planningAgeGroups() : ageGroupService.getAgeGroups());
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
        List<AgeGroupInfo> groups = (week == null || (!week.isPublished() && !isWeekPast(weekStart)))
                ? evaluationSupport.planningGroups()
                : evaluationSupport.evaluatedGroups();
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
        boolean manage = currentUser.hasPermission(Permission.MENU_MANAGE);
        boolean published = week != null && week.isPublished();
        boolean weekOpen = !isWeekPast(weekStart);
        List<MealPlanResponse.Day> dayViews = new ArrayList<>();
        for (int i = 0; i < SCHOOL_DAYS; i++) {
            LocalDate date = weekStart.plusDays(i);
            MealPlanDay day = week == null ? null : week.day(date).orElse(null);
            boolean canAmend = manage && published && (day == null || !day.isOff()) && isAmendable(date);
            dayViews.add(
                    day == null
                            ? new MealPlanResponse.Day(date, false, "", List.of(), canAmend)
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
                                            .toList(),
                                    canAmend));
        }
        MealPlanEvaluator.Result result = evaluate(weekStart, week, groups, dishes);
        List<MealPlanResponse.Issue> issues = new ArrayList<>(result.issues());
        if (week != null) {
            issues.addAll(dryStockIssues(currentUser, weekStart));
        }
        return new MealPlanResponse(
                week == null ? null : week.getId(),
                weekStart,
                week == null ? NONE : week.getStatus().name(),
                week == null ? null : week.getPublishedAt(),
                week == null ? null : week.getPublishedBy(),
                manage && !published && weekOpen,
                manage && week != null && !published && weekOpen,
                week != null && !published && result.publishable(),
                blocks,
                dayViews,
                result.evaluations(),
                issues,
                week == null || week.getId() == null ? 0 : revisionRepository.countByWeekId(week.getId()));
    }

    /**
     * NUT-11, P-38: kho khô không đủ tới hết tuần ⇒ một cảnh báo mỗi điểm trường người xem đọc được, không chặn công bố;
     * chi tiết và lượng cần nhập thêm nằm ở bản dự trù.
     */
    private List<MealPlanResponse.Issue> dryStockIssues(CurrentUser currentUser, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(SCHOOL_DAYS - 1L);
        if (weekEnd.isBefore(dryStockOutlook.today())) {
            return List.of();
        }
        List<MealPlanResponse.Issue> issues = new ArrayList<>();
        for (CampusResponse campus : campusService.getCampuses(currentUser, null)) {
            List<DryStockOutlook.Line> shortages = dryStockOutlook.shortages(campus.id(), weekEnd, null);
            if (shortages.isEmpty()) {
                continue;
            }
            LocalDate from = shortages.getFirst().shortFrom();
            issues.add(new MealPlanResponse.Issue(
                    MealPlanEvaluator.WARNING,
                    "DRY_STOCK_SHORT",
                    from,
                    null,
                    null,
                    null,
                    campus.name() + ": kho khô thiếu " + shortages.size() + " thực phẩm từ " + dayLabel(from)
                            + " — xem bản dự trù để đặt thêm"));
        }
        return issues;
    }

    private static String dayLabel(LocalDate date) {
        String day =
                switch (date.getDayOfWeek()) {
                    case MONDAY -> "thứ Hai";
                    case TUESDAY -> "thứ Ba";
                    case WEDNESDAY -> "thứ Tư";
                    case THURSDAY -> "thứ Năm";
                    case FRIDAY -> "thứ Sáu";
                    case SATURDAY -> "thứ Bảy";
                    case SUNDAY -> "Chủ nhật";
                };
        return day + " " + String.format("%02d/%02d", date.getDayOfMonth(), date.getMonthValue());
    }

    private MealPlanEvaluator.Result evaluate(LocalDate weekStart, MealPlanWeek week) {
        List<AgeGroupInfo> groups = (week == null || (!week.isPublished() && !isWeekPast(weekStart)))
                ? evaluationSupport.planningGroups()
                : evaluationSupport.evaluatedGroups();
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
        FoodPriceBook book = priceBook(dishes.values());
        return MealPlanEvaluator.evaluate(
                days,
                groups,
                MealPlanEvaluationSupport.dishInfos(dishes.values()),
                evaluationSupport.normsUntil(weekStart.plusDays(SCHOOL_DAYS - 1)),
                book::price);
    }

    private static String blockLabel(MenuBlock block) {
        return block == MenuBlock.NURSERY ? "Nhà trẻ" : "Mẫu giáo";
    }
}
