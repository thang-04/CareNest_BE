package com.carenest.service.meals.impl;

import com.carenest.config.MenuAiProperties;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.entity.meals.MenuTemplateItem;
import com.carenest.entity.meals.MenuTemplateSet;
import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.repository.meals.MenuTemplateSetRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.MealPlanService;
import com.carenest.service.meals.impl.MealPlanEvaluator.AgeGroupInfo;
import com.carenest.service.meals.impl.MealPlanEvaluator.DishInfo;
import com.carenest.service.organization.CampusService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CatalogStatus;
import com.carenest.utils.enums.DishType;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bước 1–2 của cổng giá trị: đọc dữ liệu bán trú, dựng {@link MenuContext} cho từng loại gợi ý (tuần, mẫu ngày, thay
 * một món). Gọi trong một transaction đọc ngắn; engine dùng kết quả mà không chạm DB.
 */
@Component
@RequiredArgsConstructor
class MenuAiContextLoader {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final int FAMILIAR_WEEKS = 12;
    private static final int DAYS = MealPlanEvaluationSupport.SCHOOL_DAYS;

    private final MealPlanWeekRepository weekRepository;
    private final DishRepository dishRepository;
    private final MenuTemplateSetRepository templateRepository;
    private final MealPlanEvaluationSupport evaluationSupport;
    private final MenuAiProperties properties;
    private final CampusService campusService;
    private final DryStockOutlook dryStockOutlook;
    private final MealPlanService mealPlanService;
    private final Clock clock;

    /** Thay một món: lưới tuần hiện có, ô cần thay và ngày của ô. */
    record DayFix(MenuContext ctx, MenuGrid current, LocalDate date, MenuSlot slot) {}

    /** Danh mục món: mọi món (để kiểm lưới đang có) và món dùng được (đang dùng, mọi thực phẩm đang dùng). */
    private record Catalog(
            Map<Long, Dish> all,
            List<Dish> usable,
            Map<Long, DishInfo> allInfos,
            Map<Long, DishInfo> usableInfos,
            BiFunction<Long, LocalDate, BigDecimal> priceOf) {}

    /**
     * Ràng buộc của người lập cho gợi ý tuần. {@code strict}: lúc tạo nháp, ràng buộc không áp được ⇒ 400; lúc đọc lại
     * ngữ cảnh của nháp đã có thì bỏ qua phần không còn áp được (dữ liệu có thể đã đổi).
     */
    record Constraints(Set<Long> unavailableFoodIds, Set<Long> requiredDishIds, boolean keepTemplate, boolean strict) {
        static final Constraints NONE = new Constraints(Set.of(), Set.of(), false, false);
    }

    MenuContext weekly(
            CurrentUser user,
            MenuBlock block,
            LocalDate weekStart,
            String season,
            String wishes,
            Constraints constraints,
            Map<Long, Double> similarity) {
        MealPlanWeek week = weekRepository.findByWeekStart(weekStart).orElse(null);
        checkNotPublished(week);
        List<AgeGroupInfo> groups = blockGroups(block);
        Catalog catalog = catalog(constraints.unavailableFoodIds());
        MenuTemplateSet template = templateOf(block, season);
        List<MenuSlot> slots = slotsOf(served(groups), template, catalog.all());
        Set<Long> recent = recentDishes(block, weekStart);
        MenuContext.Signals signals = signals(user, block, weekStart, season, template, catalog.usable());
        Map<Integer, Map<String, Long>> templateGrid = templateGrid(template, weekStart, slots, catalog.all());
        if (constraints.keepTemplate() && templateGrid.isEmpty() && constraints.strict()) {
            throw invalid("strategy", "Khối chưa có thực đơn mẫu đang dùng cho tuần này để chỉnh");
        }
        Set<LocalDate> offDays = offDays(week);
        Map<String, List<Long>> candidates = new LinkedHashMap<>(MenuCandidateBuilder.build(
                catalog.usableInfos().values(),
                groups,
                slots,
                recent,
                signals,
                similarity,
                properties.candidateLimit()));
        Set<Long> required = addRequired(candidates, slots, groups, catalog, constraints, DAYS - offDays.size());
        return new MenuContext(
                        block,
                        weekDates(weekStart),
                        offDays,
                        groups,
                        catalog.usableInfos(),
                        evaluationSupport.normsUntil(weekStart.plusDays(DAYS - 1L)),
                        slots,
                        candidates,
                        recent,
                        signals,
                        templateGrid,
                        MenuAiPromptBuilder.clean(season, MenuAiPromptBuilder.SEASON_MAX),
                        MenuAiPromptBuilder.clean(wishes, MenuAiPromptBuilder.WISHES_MAX))
                .priced(catalog.priceOf())
                .constrained(required, constraints.keepTemplate() && !templateGrid.isEmpty());
    }

    /**
     * Món bắt buộc phải dùng được (đang dùng, đủ định lượng, không chứa thực phẩm đang thiếu) và có ô nhận loại món đó;
     * được đưa lên đầu danh sách ứng viên của ô nhận nó kể cả khi vượt giới hạn số ứng viên. Mỗi ô nhận tối đa một món
     * bắt buộc mỗi ngày phục vụ.
     */
    private static Set<Long> addRequired(
            Map<String, List<Long>> candidates,
            List<MenuSlot> slots,
            List<AgeGroupInfo> groups,
            Catalog catalog,
            Constraints constraints,
            int servedDays) {
        Set<Long> accepted = new TreeSet<>();
        Map<String, Integer> placed = new HashMap<>();
        List<FieldErrorResponse> errors = new ArrayList<>();
        for (Long dishId : new TreeSet<>(constraints.requiredDishIds())) {
            DishInfo info = catalog.usableInfos().get(dishId);
            String name = catalog.all().containsKey(dishId)
                    ? catalog.all().get(dishId).getName()
                    : "#" + dishId;
            if (info == null || !MenuCandidateBuilder.complete(info, groups)) {
                errors.add(new FieldErrorResponse(
                        "requiredDishIds",
                        "Món " + name
                                + " không dùng được: phải đang dùng, đủ định lượng và không chứa thực phẩm thiếu"));
                continue;
            }
            MenuSlot target = slots.stream()
                    .filter(s -> s.types().contains(info.type()))
                    .filter(s -> placed.getOrDefault(s.key(), 0) < servedDays)
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                errors.add(new FieldErrorResponse(
                        "requiredDishIds", "Món " + name + ": không còn ô nào trong tuần nhận loại món này"));
                continue;
            }
            placed.merge(target.key(), 1, Integer::sum);
            accepted.add(dishId);
            for (MenuSlot slot : slots) {
                if (slot.types().contains(info.type())) {
                    List<Long> list = new ArrayList<>(candidates.get(slot.key()));
                    list.remove(dishId);
                    list.addFirst(dishId);
                    candidates.put(slot.key(), list);
                }
            }
        }
        if (!errors.isEmpty() && constraints.strict()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
        return accepted;
    }

    /**
     * Mẫu một ngày: ngày neo = hôm nay (định mức hiệu lực hôm nay); thực đơn mẫu cùng thứ của tuần xoay vòng hiện tại
     * làm phương án quy tắc ban đầu.
     */
    MenuContext daily(
            CurrentUser user,
            MenuBlock block,
            String season,
            String wishes,
            Set<Long> unavailableFoodIds,
            Map<Long, Double> similarity) {
        LocalDate anchor = LocalDate.now(clock);
        LocalDate monday = anchor.with(DayOfWeek.MONDAY);
        int weekday = Math.min(anchor.getDayOfWeek().getValue(), DAYS) - 1;
        List<AgeGroupInfo> groups = blockGroups(block);
        Catalog catalog = catalog(unavailableFoodIds);
        MenuTemplateSet template = templateOf(block, season);
        List<MenuSlot> slots = slotsOf(served(groups), template, catalog.all());
        Set<Long> recent = recentDishes(block, monday);
        MenuContext.Signals signals = signals(user, block, monday, season, template, catalog.usable());
        Map<String, Long> templateDay =
                templateGrid(template, monday, slots, catalog.all()).getOrDefault(weekday, Map.of());
        return new MenuContext(
                        block,
                        List.of(anchor),
                        Set.of(),
                        groups,
                        catalog.usableInfos(),
                        evaluationSupport.normsUntil(anchor),
                        slots,
                        MenuCandidateBuilder.build(
                                catalog.usableInfos().values(),
                                groups,
                                slots,
                                recent,
                                signals,
                                similarity,
                                properties.candidateLimit()),
                        recent,
                        signals,
                        templateDay.isEmpty() ? Map.of() : Map.of(0, templateDay),
                        MenuAiPromptBuilder.clean(season, MenuAiPromptBuilder.SEASON_MAX),
                        MenuAiPromptBuilder.clean(wishes, MenuAiPromptBuilder.WISHES_MAX))
                .priced(catalog.priceOf());
    }

    /**
     * Thay một món: ô = vị trí của món trong bữa của khối ngày đó (theo thứ tự lưu); ô khác giữ nguyên. Ứng viên cùng
     * loại món, chưa có trong bữa đó.
     */
    DayFix dayFix(
            CurrentUser user,
            MenuBlock block,
            LocalDate weekStart,
            LocalDate date,
            MealSession session,
            Long fromDishId,
            String season,
            String wishes,
            Set<Long> unavailableFoodIds,
            Map<Long, Double> similarity) {
        MealPlanWeek week = weekRepository
                .findByWeekStart(weekStart)
                .orElseThrow(() -> invalid("weekStart", "Tuần chưa có kế hoạch thực đơn để thay món"));
        if (date.isBefore(weekStart) || date.isAfter(weekStart.plusDays(DAYS - 1L))) {
            throw GlobalException.of(ApiCode.MEAL_PLAN_DATE_INVALID, date);
        }
        if (week.isPublished()) {
            // P-39: tuần đã công bố chỉ nhận gợi ý thay món ở ngày còn sửa được — cùng cờ canAmend của sửa ngoại lệ
            boolean amendable = mealPlanService.getMealPlan(user, weekStart).days().stream()
                    .anyMatch(d -> d.date().equals(date) && d.canAmend());
            if (!amendable) {
                throw GlobalException.of(ApiCode.MEAL_PLAN_DAY_LOCKED, DAY_LABEL.format(date));
            }
        }
        if (offDays(week).contains(date)) {
            throw invalid("targetDate", "Ngày nghỉ không có món để thay");
        }
        List<AgeGroupInfo> groups = blockGroups(block);
        Catalog catalog = catalog(unavailableFoodIds);
        Map<LocalDate, Map<MealSession, List<Long>>> byDay = blockItems(week, block);
        List<Long> sessionDishes = byDay.getOrDefault(date, Map.of()).getOrDefault(session, List.of());
        int index = sessionDishes.indexOf(fromDishId);
        Dish fromDish = catalog.all().get(fromDishId);
        if (index < 0 || fromDish == null) {
            throw invalid("fromDishId", "Món này không có trong bữa đã chọn của khối");
        }
        MenuSlot target = new MenuSlot(session, index, Set.of(fromDish.getDishType()));
        List<MenuSlot> slots = new ArrayList<>();
        Map<LocalDate, Map<String, Long>> cells = new TreeMap<>();
        for (MealSession s : MealSession.values()) {
            int width = byDay.values().stream()
                    .mapToInt(d -> d.getOrDefault(s, List.of()).size())
                    .max()
                    .orElse(0);
            for (int i = 0; i < width; i++) {
                slots.add(s == session && i == index ? target : new MenuSlot(s, i, Set.of()));
            }
        }
        byDay.forEach((day, sessions) -> sessions.forEach((s, dishes) -> {
            for (int i = 0; i < dishes.size(); i++) {
                cells.computeIfAbsent(day, k -> new LinkedHashMap<>()).put(s.name() + "." + (i + 1), dishes.get(i));
            }
        }));
        Set<Long> recent = recentDishes(block, weekStart);
        MenuTemplateSet template = templateOf(block, season);
        MenuContext.Signals signals = signals(user, block, weekStart, season, template, catalog.usable());
        List<Long> candidates = MenuCandidateBuilder.build(
                        catalog.usableInfos().values(),
                        groups,
                        List.of(target),
                        recent,
                        signals,
                        similarity,
                        properties.candidateLimit())
                .get(target.key())
                .stream()
                .filter(id -> !sessionDishes.contains(id))
                .toList();
        MenuContext ctx = new MenuContext(
                        block,
                        weekDates(weekStart),
                        offDays(week),
                        groups,
                        catalog.allInfos(),
                        evaluationSupport.normsUntil(weekStart.plusDays(DAYS - 1L)),
                        slots,
                        Map.of(target.key(), candidates),
                        recent,
                        signals,
                        Map.of(),
                        MenuAiPromptBuilder.clean(season, MenuAiPromptBuilder.SEASON_MAX),
                        MenuAiPromptBuilder.clean(wishes, MenuAiPromptBuilder.WISHES_MAX))
                .priced(catalog.priceOf());
        return new DayFix(ctx, new MenuGrid(cells, Map.of()), date, target);
    }

    List<AgeGroupInfo> blockGroups(MenuBlock block) {
        return evaluationSupport.planningGroups().stream()
                .filter(g -> g.block() == block)
                .sorted(Comparator.comparing(AgeGroupInfo::id))
                .toList();
    }

    /** Món của khối trong tuần: ngày → bữa → món theo thứ tự lưu. */
    static Map<LocalDate, Map<MealSession, List<Long>>> blockItems(MealPlanWeek week, MenuBlock block) {
        Map<LocalDate, Map<MealSession, List<Long>>> byDay = new TreeMap<>();
        for (MealPlanDay day : week.getDays()) {
            if (day.isOff()) {
                continue;
            }
            day.getItems().stream()
                    .filter(i -> i.getMenuBlock() == block)
                    .sorted(Comparator.comparingInt(MealPlanItem::getSortOrder))
                    .forEach(i -> byDay.computeIfAbsent(day.getMenuDate(), k -> new TreeMap<>())
                            .computeIfAbsent(i.getSession(), k -> new ArrayList<>())
                            .add(i.getDishId()));
        }
        return byDay;
    }

    private Catalog catalog(Set<Long> unavailableFoodIds) {
        Map<Long, Dish> all = dishRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.toMap(Dish::getId, Function.identity()));
        // Món đang dùng và mọi thực phẩm của món còn đang dùng (thực phẩm ngừng dùng = trường không còn mua), không
        // chứa thực phẩm người lập báo đang thiếu
        List<Dish> usable = all.values().stream()
                .filter(d -> d.getStatus() == CatalogStatus.ACTIVE)
                .filter(d -> d.getIngredients().stream()
                        .allMatch(i -> i.getFood().getStatus() == CatalogStatus.ACTIVE
                                && !unavailableFoodIds.contains(i.getFood().getId())))
                .toList();
        return new Catalog(
                all,
                usable,
                MealPlanEvaluationSupport.dishInfos(all.values()),
                MealPlanEvaluationSupport.dishInfos(usable),
                evaluationSupport.priceOf(all.values()));
    }

    private static void checkNotPublished(MealPlanWeek week) {
        // NUT-08: tuần đã công bố không nhận gợi ý
        if (week != null && week.isPublished()) {
            throw GlobalException.of(ApiCode.MEAL_PLAN_PUBLISHED);
        }
    }

    private static GlobalException invalid(String field, String message) {
        return GlobalException.withFieldErrors(
                ApiCode.INVALID_REQUEST_DATA, List.of(new FieldErrorResponse(field, message)));
    }

    private static List<LocalDate> weekDates(LocalDate weekStart) {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            dates.add(weekStart.plusDays(i));
        }
        return dates;
    }

    private static Set<LocalDate> offDays(MealPlanWeek week) {
        return week == null
                ? Set.of()
                : week.getDays().stream()
                        .filter(MealPlanDay::isOff)
                        .map(MealPlanDay::getMenuDate)
                        .collect(Collectors.toSet());
    }

    private static Set<MealSession> served(List<AgeGroupInfo> groups) {
        return groups.stream().flatMap(g -> g.sessions().stream()).collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Tín hiệu từ dữ liệu của trường: món của bộ mẫu trùng nhãn mùa (NUT-13); món đã có trong tuần đã công bố 12 tuần
     * gần đây; món dùng thực phẩm kho khô mà mọi điểm trường người lập xem được đều hết hàng còn hạn tại {@code onDate}
     * (NUT-11; hàng tươi đặt theo bản dự trù nên không xét, NUT-12).
     */
    private MenuContext.Signals signals(
            CurrentUser user,
            MenuBlock block,
            LocalDate onDate,
            String season,
            MenuTemplateSet template,
            List<Dish> usable) {
        boolean seasonMatch = template != null
                && season != null
                && !season.isBlank()
                && template.getSeasonLabel().equalsIgnoreCase(season.trim());
        Set<Long> seasonal = seasonMatch
                ? template.getItems().stream().map(MenuTemplateItem::getDishId).collect(Collectors.toSet())
                : Set.of();
        Set<Long> familiar = weekRepository
                .findWithDaysByWeekStartBetweenOrderByWeekStartAsc(
                        onDate.minusWeeks(FAMILIAR_WEEKS), onDate.minusWeeks(1))
                .stream()
                .filter(MealPlanWeek::isPublished)
                .flatMap(w -> w.getDays().stream())
                .flatMap(d -> d.getItems().stream())
                .filter(i -> i.getMenuBlock() == block)
                .map(MealPlanItem::getDishId)
                .collect(Collectors.toSet());
        Map<Long, BigDecimal> stock = new HashMap<>();
        campusService
                .getCampuses(user, null)
                .forEach(c -> dryStockOutlook
                        .usableBalances(c.id(), onDate)
                        .forEach((foodId, qty) -> stock.merge(foodId, qty, BigDecimal::add)));
        Set<Long> outOfStock = usable.stream()
                .filter(d -> d.getIngredients().stream()
                        .anyMatch(i -> i.getFood().getStorage() == FoodStorage.DRY
                                && stock.getOrDefault(i.getFood().getId(), BigDecimal.ZERO)
                                                .signum()
                                        <= 0))
                .map(Dish::getId)
                .collect(Collectors.toSet());
        return new MenuContext.Signals(seasonal, seasonMatch ? template.getSeasonLabel() : null, familiar, outOfStock);
    }

    // Bộ mẫu đang dùng của khối; trùng nhãn mùa PHT nhập thì ưu tiên
    private MenuTemplateSet templateOf(MenuBlock block, String season) {
        List<MenuTemplateSet> sets = templateRepository.findAllByOrderByMenuBlockAscNameAsc().stream()
                .filter(s -> s.getMenuBlock() == block && s.getStatus() == CatalogStatus.ACTIVE)
                .toList();
        return sets.stream()
                .filter(s -> season != null && s.getSeasonLabel().equalsIgnoreCase(season.trim()))
                .findFirst()
                .orElse(sets.isEmpty() ? null : sets.getFirst());
    }

    /**
     * Khung ô của mỗi bữa: chuỗi loại món hay gặp nhất của bữa đó trong thực đơn mẫu (vd. trưa: cơm, mặn, canh, rau);
     * bữa mẫu không có thì theo khung mặc định trong cấu hình.
     */
    private List<MenuSlot> slotsOf(Set<MealSession> served, MenuTemplateSet template, Map<Long, Dish> dishes) {
        List<MenuSlot> slots = new ArrayList<>();
        for (MealSession session : served) {
            List<Set<DishType>> frame = templateFrame(session, template, dishes);
            if (frame.isEmpty()) {
                frame = properties.defaultSlots().getOrDefault(session, List.of()).stream()
                        .map(types -> (Set<DishType>) EnumSet.copyOf(types))
                        .toList();
            }
            if (frame.isEmpty()) {
                frame = List.of(EnumSet.allOf(DishType.class));
            }
            for (int i = 0; i < frame.size(); i++) {
                slots.add(new MenuSlot(session, i, frame.get(i)));
            }
        }
        return slots;
    }

    private static List<Set<DishType>> templateFrame(
            MealSession session, MenuTemplateSet template, Map<Long, Dish> dishes) {
        if (template == null) {
            return List.of();
        }
        Map<String, List<MenuTemplateItem>> byDay = new TreeMap<>();
        template.getItems().stream()
                .filter(i -> i.getSession() == session && dishes.containsKey(i.getDishId()))
                .forEach(i -> byDay.computeIfAbsent(i.getWeekNo() + "|" + i.getWeekday(), k -> new ArrayList<>())
                        .add(i));
        Map<List<DishType>, Integer> counts = new LinkedHashMap<>();
        byDay.values()
                .forEach(items -> counts.merge(
                        items.stream()
                                .sorted(Comparator.comparingInt(MenuTemplateItem::getSortOrder))
                                .map(i -> dishes.get(i.getDishId()).getDishType())
                                .toList(),
                        1,
                        Integer::sum));
        return counts.entrySet().stream()
                .max(Map.Entry.<List<DishType>, Integer>comparingByValue())
                .map(e -> e.getKey().stream()
                        .map(t -> (Set<DishType>) EnumSet.of(t))
                        .toList())
                .orElse(List.of());
    }

    // Tuần của bộ mẫu xoay vòng 4 tuần theo số tuần kể từ mốc thứ Hai 05/01/1970
    private static Map<Integer, Map<String, Long>> templateGrid(
            MenuTemplateSet template, LocalDate weekStart, List<MenuSlot> slots, Map<Long, Dish> dishes) {
        if (template == null) {
            return Map.of();
        }
        int weekNo = (int) Math.floorMod((weekStart.toEpochDay() - 4) / 7, MenuTemplateSet.WEEKS) + 1;
        Map<Integer, Map<String, Long>> grid = new HashMap<>();
        Map<String, List<MenuTemplateItem>> byCell = new TreeMap<>();
        template.getItems().stream()
                .filter(i -> i.getWeekNo() == weekNo && dishes.containsKey(i.getDishId()))
                .forEach(i -> byCell.computeIfAbsent(i.getWeekday() + "|" + i.getSession(), k -> new ArrayList<>())
                        .add(i));
        byCell.values().forEach(items -> {
            items.sort(Comparator.comparingInt(MenuTemplateItem::getSortOrder));
            int dayIndex = items.getFirst().getWeekday() - 1;
            Map<String, Long> day = grid.computeIfAbsent(dayIndex, k -> new HashMap<>());
            for (MenuTemplateItem item : items) {
                DishType type = dishes.get(item.getDishId()).getDishType();
                slots.stream()
                        .filter(s ->
                                s.session() == item.getSession() && s.types().contains(type))
                        .filter(s -> !day.containsKey(s.key()))
                        .findFirst()
                        .ifPresent(s -> day.put(s.key(), item.getDishId()));
            }
        });
        return grid;
    }

    /** Thứ Hai đầu tiên của khoảng lịch sử dùng để tránh lặp món: các tuần liền trước tuần {@code weekStart}. */
    LocalDate historyFrom(LocalDate weekStart) {
        return weekStart.minusWeeks(properties.recentWeeks());
    }

    // Lịch sử lấy theo ngày bắt đầu tuần, không theo độ giống nội dung
    private Set<Long> recentDishes(MenuBlock block, LocalDate weekStart) {
        return weekRepository
                .findWithDaysByWeekStartBetweenOrderByWeekStartAsc(historyFrom(weekStart), weekStart.minusWeeks(1))
                .stream()
                .flatMap(w -> w.getDays().stream())
                .flatMap(d -> d.getItems().stream())
                .filter(i -> i.getMenuBlock() == block)
                .map(MealPlanItem::getDishId)
                .collect(Collectors.toSet());
    }
}
