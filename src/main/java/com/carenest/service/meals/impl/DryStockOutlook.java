package com.carenest.service.meals.impl;

import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.FreshForecast;
import com.carenest.entity.meals.FreshForecastCount;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.entity.meals.StockLot;
import com.carenest.repository.meals.FreshForecastRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.repository.meals.StockDailyDemandRepository;
import com.carenest.repository.meals.StockLotRepository;
import com.carenest.repository.meals.StockMovementRepository;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MenuBlock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * NUT-11, P-38: kho khô sắp thiếu của một điểm trường. Đi từng ngày chưa chốt suất từ hôm nay, nhu cầu theo sĩ số dự kiến
 * (NUT-07), lấy lô hết hạn trước và bỏ lô hết hạn trước ngày ăn như trừ thật; phần thiếu đang mở được bù trước vì phiếu
 * nhập trừ bù ngày cũ trước. Chỉ tính để cảnh báo, không ghi sổ kho.
 */
@Component
@RequiredArgsConstructor
class DryStockOutlook {
    private final StockLotRepository lotRepository;
    private final StockMovementRepository movementRepository;
    private final StockDailyDemandRepository demandRepository;
    private final MealPlanWeekRepository weekRepository;
    private final MealCountRepository mealCountRepository;
    private final FreshForecastRepository forecastRepository;
    private final MealDemandCalculator demandCalculator;
    private final Clock clock;

    /** {@code unitPrice}: giá theo đơn vị mua của lô (phiếu nhập); null khi phiếu không ghi giá. */
    record Lot(Long foodId, LocalDate expiryDate, BigDecimal balance, BigDecimal unitPrice) {
        Lot(Long foodId, LocalDate expiryDate, BigDecimal balance) {
            this(foodId, expiryDate, balance, null);
        }
    }

    /**
     * Kết quả lấy lô cho một thực phẩm: {@code lotQty} lượng (g/ml) lấy từ lô có giá, {@code lotValue} = Σ lượng × giá
     * lô (chưa chia cỡ đơn vị mua), {@code catalogQty} lượng còn tính theo giá danh mục (thiếu kho hoặc lô không ghi
     * giá), {@code shortQty} lượng thiếu kho.
     */
    record LotTake(BigDecimal lotQty, BigDecimal lotValue, BigDecimal catalogQty, BigDecimal shortQty) {}

    /**
     * {@code requiredQty}: nhu cầu từ hôm nay tới hết khung; {@code availableQty}: tồn còn dùng được sau khi bù phần thiếu
     * đang mở; {@code shortQty}: lượng cần nhập thêm (gồm cả phần thiếu đang mở chưa bù được); {@code shortFrom}: ngày
     * bắt đầu thiếu.
     */
    record Line(
            Long foodId, BigDecimal requiredQty, BigDecimal availableQty, BigDecimal shortQty, LocalDate shortFrom) {
        boolean isShort() {
            return shortQty.signum() > 0;
        }
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Dòng thiếu của điểm trường từ hôm nay tới {@code to}, xếp theo ngày bắt đầu thiếu. {@code servingsOverride} trả
     * suất dự kiến cho ngày; trả null ⇒ bản dự trù mới lập nhất có kỳ chứa ngày, không có thì sĩ số đang học.
     */
    List<Line> shortages(
            Long campusId, LocalDate to, Function<LocalDate, List<MealDemandCalculator.Servings>> servingsOverride) {
        LocalDate from = today();
        if (to.isBefore(from)) {
            return List.of();
        }
        SortedMap<LocalDate, MealPlanDay> days = openDays(campusId, from, to);
        if (days.isEmpty()) {
            return List.of();
        }
        Map<Long, Dish> dishes = demandCalculator.dishesOf(days.values());
        Map<Long, MenuBlock> blocks = demandCalculator.blockOfAgeGroup();
        Map<Long, Integer> enrolled = null;
        SortedMap<LocalDate, Map<Long, BigDecimal>> demand = new TreeMap<>();
        for (Map.Entry<LocalDate, MealPlanDay> entry : days.entrySet()) {
            LocalDate date = entry.getKey();
            List<MealDemandCalculator.Servings> servings =
                    servingsOverride == null ? null : servingsOverride.apply(date);
            if (servings == null) {
                Map<Long, Integer> forecastCounts = forecastCounts(campusId, date);
                if (forecastCounts == null && enrolled == null) {
                    enrolled = demandCalculator.enrolledByAgeGroup(campusId, from);
                }
                servings = demandCalculator.expectedServings(forecastCounts == null ? enrolled : forecastCounts);
            }
            demand.put(date, MealDemandCalculator.compute(entry.getValue(), dishes, blocks, servings, FoodStorage.DRY));
        }
        return project(from, lots(campusId), openShortfalls(campusId), demand).stream()
                .filter(Line::isShort)
                .sorted(Comparator.comparing(Line::shortFrom).thenComparing(Line::foodId))
                .toList();
    }

    /**
     * Mô phỏng thuần: bù phần thiếu đang mở trước (mọi lô, hết hạn trước), rồi từng ngày tăng dần lấy lô hết hạn trước
     * trong các lô còn hạn tới ngày ăn. Trả mọi thực phẩm có nhu cầu hoặc phần thiếu đang mở.
     */
    static List<Line> project(
            LocalDate from,
            List<Lot> lots,
            Map<Long, BigDecimal> openShortfalls,
            SortedMap<LocalDate, Map<Long, BigDecimal>> demandByDate) {
        Map<Long, List<Lot>> lotsByFood = lots.stream()
                .filter(lot -> lot.balance().signum() > 0)
                .sorted(Comparator.comparing(Lot::expiryDate))
                .collect(Collectors.groupingBy(Lot::foodId, LinkedHashMap::new, Collectors.toList()));
        Set<Long> foodIds = new TreeSet<>(openShortfalls.keySet());
        demandByDate
                .values()
                .forEach(byFood -> byFood.forEach((foodId, qty) -> {
                    if (qty.signum() > 0) {
                        foodIds.add(foodId);
                    }
                }));
        List<Line> lines = new ArrayList<>();
        for (Long foodId : foodIds) {
            List<Lot> foodLots = lotsByFood.getOrDefault(foodId, List.of());
            BigDecimal[] left = foodLots.stream().map(Lot::balance).toArray(BigDecimal[]::new);
            BigDecimal owed = openShortfalls.getOrDefault(foodId, BigDecimal.ZERO);
            BigDecimal unpaid = owed.subtract(take(foodLots, left, owed, null));
            BigDecimal available = BigDecimal.ZERO;
            for (int i = 0; i < foodLots.size(); i++) {
                if (!foodLots.get(i).expiryDate().isBefore(from)) {
                    available = available.add(left[i]);
                }
            }
            BigDecimal required = BigDecimal.ZERO;
            BigDecimal shortQty = unpaid;
            LocalDate shortFrom = unpaid.signum() > 0 ? from : null;
            for (Map.Entry<LocalDate, Map<Long, BigDecimal>> day : demandByDate.entrySet()) {
                BigDecimal need = day.getValue().getOrDefault(foodId, BigDecimal.ZERO);
                if (need.signum() <= 0) {
                    continue;
                }
                required = required.add(need);
                BigDecimal missing = need.subtract(take(foodLots, left, need, day.getKey()));
                if (missing.signum() > 0) {
                    shortQty = shortQty.add(missing);
                    if (shortFrom == null) {
                        shortFrom = day.getKey();
                    }
                }
            }
            lines.add(new Line(foodId, required, available, shortQty, shortFrom));
        }
        return lines;
    }

    /** NUT-27: giá lô hàng khô trong thống kê — FEFO trên tồn hiện tại của điểm trường, mỗi thực phẩm riêng. */
    Map<Long, LotTake> lotPricing(Long campusId, Map<Long, BigDecimal> needByFood, LocalDate mealDate) {
        return needByFood.isEmpty() ? Map.of() : priceLots(lots(campusId), needByFood, mealDate);
    }

    static Map<Long, LotTake> priceLots(List<Lot> lots, Map<Long, BigDecimal> needByFood, LocalDate mealDate) {
        Map<Long, List<Lot>> byFood =
                lots.stream().sorted(Comparator.comparing(Lot::expiryDate)).collect(Collectors.groupingBy(Lot::foodId));
        Map<Long, LotTake> result = new HashMap<>();
        needByFood.forEach((foodId, need) -> {
            BigDecimal remaining = need;
            BigDecimal lotQty = BigDecimal.ZERO;
            BigDecimal lotValue = BigDecimal.ZERO;
            BigDecimal unpriced = BigDecimal.ZERO;
            for (Lot lot : byFood.getOrDefault(foodId, List.of())) {
                if (remaining.signum() <= 0) {
                    break;
                }
                if (lot.balance().signum() <= 0
                        || (mealDate != null && lot.expiryDate().isBefore(mealDate))) {
                    continue;
                }
                BigDecimal taken = lot.balance().min(remaining);
                remaining = remaining.subtract(taken);
                if (lot.unitPrice() == null) {
                    unpriced = unpriced.add(taken);
                } else {
                    lotQty = lotQty.add(taken);
                    lotValue = lotValue.add(taken.multiply(lot.unitPrice()));
                }
            }
            result.put(foodId, new LotTake(lotQty, lotValue, remaining.add(unpriced), remaining));
        });
        return result;
    }

    /** Lấy tối đa {@code amount} từ các lô theo thứ tự hết hạn trước; {@code mealDate} null ⇒ không lọc hạn dùng. */
    private static BigDecimal take(List<Lot> lots, BigDecimal[] left, BigDecimal amount, LocalDate mealDate) {
        BigDecimal remaining = amount;
        for (int i = 0; i < lots.size() && remaining.signum() > 0; i++) {
            if (left[i].signum() <= 0
                    || (mealDate != null && lots.get(i).expiryDate().isBefore(mealDate))) {
                continue;
            }
            BigDecimal taken = left[i].min(remaining);
            left[i] = left[i].subtract(taken);
            remaining = remaining.subtract(taken);
        }
        return amount.subtract(remaining);
    }

    /** Ngày còn phải trừ kho: có thực đơn tuần (nháp hoặc đã công bố), không nghỉ, điểm trường chưa chốt suất. */
    private SortedMap<LocalDate, MealPlanDay> openDays(Long campusId, LocalDate from, LocalDate to) {
        Set<LocalDate> confirmed = mealCountRepository.findByDateBetween(from, to).stream()
                .filter(c -> campusId.equals(c.getCampusId()) && c.isConfirmed())
                .map(MealCount::getDate)
                .collect(Collectors.toSet());
        Map<LocalDate, MealPlanWeek> weeks = weekRepository
                .findWithDaysByWeekStartBetweenOrderByWeekStartAsc(
                        from.with(DayOfWeek.MONDAY), to.with(DayOfWeek.MONDAY))
                .stream()
                .collect(Collectors.toMap(MealPlanWeek::getWeekStart, Function.identity()));
        SortedMap<LocalDate, MealPlanDay> days = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            MealPlanWeek week = weeks.get(date.with(DayOfWeek.MONDAY));
            MealPlanDay day = week == null ? null : week.day(date).orElse(null);
            if (day != null && !day.isOff() && !confirmed.contains(date) && !isWeekend(date)) {
                days.put(date, day);
            }
        }
        return days;
    }

    /** Số dự kiến của bản dự trù mới lập nhất có kỳ chứa ngày (P-24); null khi chưa có. */
    private Map<Long, Integer> forecastCounts(Long campusId, LocalDate date) {
        FreshForecast forecast = forecastRepository.findCovering(campusId, date).stream()
                .findFirst()
                .orElse(null);
        if (forecast == null) {
            return null;
        }
        return forecast.getCounts().stream()
                .collect(Collectors.toMap(
                        FreshForecastCount::ageGroupId, FreshForecastCount::expectedCount, Integer::sum));
    }

    private List<Lot> lots(Long campusId) {
        List<StockLot> lots = lotRepository.findByCampusIdOrderByExpiryDateAscIdAsc(campusId);
        if (lots.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> balances =
                movementRepository
                        .sumByLotIds(lots.stream().map(StockLot::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(
                                StockMovementRepository.LotBalance::getLotId,
                                StockMovementRepository.LotBalance::getBalance));
        return lots.stream()
                .map(lot -> new Lot(
                        lot.getFoodId(),
                        lot.getExpiryDate(),
                        balances.getOrDefault(lot.getId(), BigDecimal.ZERO),
                        lot.getUnitPrice()))
                .toList();
    }

    private Map<Long, BigDecimal> openShortfalls(Long campusId) {
        Map<Long, BigDecimal> owed = new HashMap<>();
        demandRepository
                .findByCampusIdAndShortfallQtyGreaterThanOrderByMealDateAscFoodIdAsc(campusId, BigDecimal.ZERO)
                .forEach(row -> owed.merge(row.getFoodId(), row.getShortfallQty(), BigDecimal::add));
        return owed;
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
