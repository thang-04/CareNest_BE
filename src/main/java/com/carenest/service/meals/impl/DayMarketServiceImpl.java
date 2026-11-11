package com.carenest.service.meals.impl;

import com.carenest.dto.meals.DayMarketResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.entity.meals.AgeGroupMealConfig;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.StockMapper;
import com.carenest.repository.meals.AgeGroupMealConfigRepository;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.repository.meals.FreshForecastRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.meals.DayMarketService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class DayMarketServiceImpl implements DayMarketService {
    private static final String LOG_PREFIX = "[DayMarketServiceImpl]";
    private static final String READY = "READY";
    private static final String DAY_OFF = "DAY_OFF";
    private static final String NO_MENU = "NO_MENU";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String FORECAST = "FORECAST";

    private final MealPlanWeekRepository weekRepository;
    private final MealCountRepository mealCountRepository;
    private final FoodRepository foodRepository;
    private final AgeGroupMealConfigRepository configRepository;
    private final FreshForecastRepository campusLookup;
    private final MealDemandCalculator demandCalculator;
    private final AgeGroupService ageGroupService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final StockMapper stockMapper;
    private final Clock clock;

    /** Lượng và tiền của một thực phẩm cho một nhóm tuổi. */
    private record Share(BigDecimal quantity, BigDecimal cost) {
        static final Share NONE = new Share(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Override
    public DayMarketResponse getDayMarket(CurrentUser currentUser, Long campusId, LocalDate date) {
        String logPrefix =
                LOG_PREFIX + "|getDayMarket|userId=" + currentUser.userId() + "|campusId=" + campusId + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            if (!campusLookup.campusExists(campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_NOT_FOUND);
            }
            DayMarketResponse response = build(campusId, date == null ? LocalDate.now(clock) : date);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private DayMarketResponse build(Long campusId, LocalDate date) {
        MealPlanWeek week =
                weekRepository.findByWeekStart(date.with(DayOfWeek.MONDAY)).orElse(null);
        MealPlanDay day = week == null ? null : week.day(date).orElse(null);
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY
                || (day != null && day.isOff())) {
            return empty(campusId, date, DAY_OFF);
        }
        // Đi chợ theo thực đơn đã công bố; tuần nháp chưa dùng được
        if (week == null || !week.isPublished() || day == null) {
            return empty(campusId, date, NO_MENU);
        }

        List<MealCount> counts = mealCountRepository.findByCampusIdAndDate(campusId, date);
        // NUT-03: chỉ dùng số chốt khi mọi sổ báo ăn của ngày đã xác nhận; chưa đủ thì tính theo sĩ số đang học
        // (NUT-07)
        boolean confirmed = !counts.isEmpty() && counts.stream().allMatch(MealCount::isConfirmed);
        List<MealDemandCalculator.Servings> servings = (confirmed
                        ? demandCalculator.confirmedServings(counts)
                        : demandCalculator.expectedServings(demandCalculator.enrolledByAgeGroup(campusId, date)))
                .stream().filter(s -> s.portions() > 0).toList();
        Instant confirmedAt = confirmed
                ? counts.stream()
                        .map(MealCount::getConfirmedAt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null)
                : null;

        Map<Long, List<MealDemandCalculator.Servings>> servingsByGroup =
                servings.stream().collect(Collectors.groupingBy(MealDemandCalculator.Servings::ageGroupId));
        List<AgeGroupDetailResponse> ageGroups = ageGroupService.getAgeGroups().stream()
                .filter(g -> servingsByGroup.containsKey(g.id()))
                .sorted(Comparator.comparing(
                                AgeGroupDetailResponse::sortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AgeGroupDetailResponse::id))
                .toList();
        Map<Long, MenuBlock> blocks = demandCalculator.blockOfAgeGroup();
        Map<Long, Dish> dishes = demandCalculator.dishesOf(List.of(day));

        // foodId → nhóm tuổi → lượng thực dùng (đơn vị gốc)
        Map<Long, Map<Long, BigDecimal>> quantities = new HashMap<>();
        for (AgeGroupDetailResponse group : ageGroups) {
            for (FoodStorage storage : FoodStorage.values()) {
                MealDemandCalculator.compute(day, dishes, blocks, servingsByGroup.get(group.id()), storage)
                        .forEach((foodId, qty) -> {
                            if (qty.signum() > 0) {
                                quantities
                                        .computeIfAbsent(foodId, id -> new HashMap<>())
                                        .put(group.id(), qty);
                            }
                        });
            }
        }
        Map<Long, Food> foods = quantities.isEmpty()
                ? Map.of()
                : foodRepository.findAllById(quantities.keySet()).stream()
                        .collect(Collectors.toMap(Food::getId, Function.identity()));

        List<DayMarketResponse.DayMarketLine> lines = new ArrayList<>();
        Map<Long, BigDecimal> costByGroup = new HashMap<>();
        BigDecimal freshCost = BigDecimal.ZERO;
        BigDecimal dryCost = BigDecimal.ZERO;
        for (Map.Entry<Long, Map<Long, BigDecimal>> entry : quantities.entrySet()) {
            Food food = foods.get(entry.getKey());
            if (food == null) {
                continue;
            }
            List<DayMarketResponse.DayMarketGroupShare> shares = new ArrayList<>();
            BigDecimal quantity = BigDecimal.ZERO;
            BigDecimal cost = BigDecimal.ZERO;
            for (AgeGroupDetailResponse group : ageGroups) {
                BigDecimal qty = entry.getValue().get(group.id());
                Share share = qty == null ? Share.NONE : new Share(qty, costOf(qty, food));
                shares.add(new DayMarketResponse.DayMarketGroupShare(group.id(), share.quantity(), share.cost()));
                quantity = quantity.add(share.quantity());
                cost = cost.add(share.cost());
                costByGroup.merge(group.id(), share.cost(), BigDecimal::add);
            }
            if (food.getStorage() == FoodStorage.DRY) {
                dryCost = dryCost.add(cost);
            } else {
                freshCost = freshCost.add(cost);
            }
            lines.add(new DayMarketResponse.DayMarketLine(
                    stockMapper.toFoodRef(food),
                    food.getStorage().name(),
                    food.getUnitPrice(),
                    quantity,
                    PurchaseUnits.roundUp(quantity, food),
                    cost,
                    shares));
        }
        // Hàng mua chợ trước, hàng xuất kho sau; trong mỗi phần theo tên
        lines.sort(Comparator.comparing((DayMarketResponse.DayMarketLine line) ->
                        FoodStorage.DRY.name().equals(line.storage()))
                .thenComparing(line -> line.food().name()));

        Map<Long, BigDecimal> prices = mealPrices(date);
        List<DayMarketResponse.DayMarketGroup> groups = new ArrayList<>();
        int totalServings = 0;
        BigDecimal revenue = null;
        boolean priceMissing = false;
        for (AgeGroupDetailResponse group : ageGroups) {
            int paid = paidServings(servingsByGroup.get(group.id()));
            BigDecimal price = prices.get(group.id());
            BigDecimal groupRevenue = price == null ? null : price.multiply(BigDecimal.valueOf(paid));
            groups.add(new DayMarketResponse.DayMarketGroup(group.id(), group.name(), paid, price, groupRevenue));
            totalServings += paid;
            if (groupRevenue == null) {
                priceMissing = true;
            } else {
                revenue = revenue == null ? groupRevenue : revenue.add(groupRevenue);
            }
        }
        BigDecimal totalCost = freshCost.add(dryCost);
        DayMarketResponse.DayMarketTotals totals = new DayMarketResponse.DayMarketTotals(
                totalServings,
                revenue,
                totalCost,
                freshCost,
                dryCost,
                revenue == null || priceMissing ? null : revenue.subtract(totalCost),
                ageGroups.stream()
                        .map(g -> new DayMarketResponse.DayMarketGroupCost(
                                g.id(), costByGroup.getOrDefault(g.id(), BigDecimal.ZERO)))
                        .toList());
        return new DayMarketResponse(
                campusId,
                date,
                READY,
                confirmed ? CONFIRMED : FORECAST,
                confirmedAt,
                groups,
                lines,
                totals,
                menu(day, dishes, blocks, ageGroups, servingsByGroup));
    }

    private static DayMarketResponse empty(Long campusId, LocalDate date, String status) {
        BigDecimal zero = BigDecimal.ZERO;
        return new DayMarketResponse(
                campusId,
                date,
                status,
                null,
                null,
                List.of(),
                List.of(),
                new DayMarketResponse.DayMarketTotals(0, null, zero, zero, zero, null, List.of()),
                List.of());
    }

    // Tiền theo lượng thực dùng: lượng ÷ quy cách mua × đơn giá đơn vị mua, làm tròn đồng (cùng cách thống kê thực đơn)
    private static BigDecimal costOf(BigDecimal quantity, Food food) {
        BigDecimal size = food.getPurchaseUnitSize();
        if (size == null || size.signum() <= 0 || food.getUnitPrice() == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(food.getUnitPrice()).divide(size, 0, RoundingMode.HALF_UP);
    }

    // Tiền ăn thu theo trẻ/ngày ⇒ suất tính tiền lấy bữa trưa; nhóm không ăn trưa lấy bữa đầu tiên có suất
    private static int paidServings(List<MealDemandCalculator.Servings> servings) {
        return servings.stream()
                .filter(s -> s.session() == MealSession.LUNCH)
                .findFirst()
                .or(() -> servings.stream().min(Comparator.comparing(MealDemandCalculator.Servings::session)))
                .map(MealDemandCalculator.Servings::portions)
                .orElse(0);
    }

    /** NUT-17: tiền ăn một trẻ một ngày theo cấu hình hiệu lực gần nhất tính đến {@code date} của từng nhóm tuổi. */
    private Map<Long, BigDecimal> mealPrices(LocalDate date) {
        Map<Long, BigDecimal> prices = new HashMap<>();
        for (AgeGroupMealConfig config :
                configRepository.findByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(date)) {
            prices.putIfAbsent(config.getAgeGroupId(), config.getMealPrice());
        }
        return prices;
    }

    private static List<DayMarketResponse.DayMarketSessionMenu> menu(
            MealPlanDay day,
            Map<Long, Dish> dishes,
            Map<Long, MenuBlock> blocks,
            List<AgeGroupDetailResponse> ageGroups,
            Map<Long, List<MealDemandCalculator.Servings>> servingsByGroup) {
        Map<MealSession, List<DayMarketResponse.DayMarketGroupDishes>> bySession = new LinkedHashMap<>();
        for (MealSession session : MealSession.values()) {
            for (AgeGroupDetailResponse group : ageGroups) {
                boolean served = servingsByGroup.get(group.id()).stream().anyMatch(s -> s.session() == session);
                if (!served) {
                    continue;
                }
                List<String> names = day.getItems().stream()
                        .filter(item -> item.getSession() == session && item.getMenuBlock() == blocks.get(group.id()))
                        .map(MealPlanItem::getDishId)
                        .map(dishes::get)
                        .filter(Objects::nonNull)
                        .map(Dish::getName)
                        .toList();
                if (!names.isEmpty()) {
                    bySession
                            .computeIfAbsent(session, s -> new ArrayList<>())
                            .add(new DayMarketResponse.DayMarketGroupDishes(group.id(), names));
                }
            }
        }
        return bySession.entrySet().stream()
                .map(e -> new DayMarketResponse.DayMarketSessionMenu(e.getKey(), e.getValue()))
                .toList();
    }
}
