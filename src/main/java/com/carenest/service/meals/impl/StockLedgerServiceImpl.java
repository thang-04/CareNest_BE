package com.carenest.service.meals.impl;

import com.carenest.dto.common.PageResponse;
import com.carenest.dto.meals.StockLotResponse;
import com.carenest.dto.meals.StockMovementResponse;
import com.carenest.dto.meals.StockShortfallResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.DishIngredient;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealCountLine;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanItem;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.entity.meals.StockDailyDemand;
import com.carenest.entity.meals.StockLot;
import com.carenest.entity.meals.StockMovement;
import com.carenest.entity.meals.Supplier;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.StockMapper;
import com.carenest.repository.meals.DishRepository;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.repository.meals.StockDailyDemandRepository;
import com.carenest.repository.meals.StockLotRepository;
import com.carenest.repository.meals.StockMovementRepository;
import com.carenest.repository.meals.SupplierRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.meals.StockLedgerService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.MealSession;
import com.carenest.utils.enums.MenuBlock;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.StockMovementType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StockLedgerServiceImpl implements StockLedgerService {
    private static final String LOG_PREFIX = "[StockLedgerServiceImpl]";
    private static final int DEFAULT_HISTORY_DAYS = 30;
    private static final int MAX_PAGE_SIZE = 200;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StockLotRepository lotRepository;
    private final StockMovementRepository movementRepository;
    private final StockDailyDemandRepository demandRepository;
    private final MealPlanWeekRepository weekRepository;
    private final MealCountRepository mealCountRepository;
    private final DishRepository dishRepository;
    private final FoodRepository foodRepository;
    private final SupplierRepository supplierRepository;
    private final ClassroomService classroomService;
    private final AgeGroupService ageGroupService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final StockMapper mapper;
    private final Clock clock;

    /** Suất đã chốt của một bữa cho một nhóm tuổi. */
    private record Servings(MealSession session, Long ageGroupId) {}

    @Override
    @Transactional(readOnly = true)
    public List<StockLotResponse> getLots(CurrentUser currentUser, Long campusId, Long foodId, boolean includeEmpty) {
        String logPrefix = LOG_PREFIX + "|getLots|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            List<StockLot> lots = foodId == null
                    ? lotRepository.findByCampusIdOrderByExpiryDateAscIdAsc(campusId)
                    : lotRepository.findByCampusIdAndFoodIdOrderByExpiryDateAscIdAsc(campusId, foodId);
            Map<Long, BigDecimal> balances = balances(lots);
            List<StockLot> shown = lots.stream()
                    .filter(lot -> includeEmpty
                            || balances.getOrDefault(lot.getId(), BigDecimal.ZERO)
                                            .signum()
                                    > 0)
                    .toList();
            Map<Long, Food> foods =
                    foods(shown.stream().map(StockLot::getFoodId).toList());
            Map<Long, String> suppliers =
                    supplierRepository
                            .findAllById(
                                    shown.stream().map(StockLot::getSupplierId).collect(Collectors.toSet()))
                            .stream()
                            .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
            List<StockLotResponse> response = shown.stream()
                    .map(lot -> mapper.toResponse(
                            lot,
                            foods.get(lot.getFoodId()),
                            suppliers.get(lot.getSupplierId()),
                            balances.getOrDefault(lot.getId(), BigDecimal.ZERO)))
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
    public PageResponse<StockMovementResponse> getMovements(
            CurrentUser currentUser,
            Long campusId,
            LocalDate from,
            LocalDate to,
            Long foodId,
            StockMovementType type,
            int page,
            int size) {
        String logPrefix = LOG_PREFIX + "|getMovements|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            ZoneId zone = clock.getZone();
            LocalDate end = to == null ? LocalDate.now(clock) : to;
            LocalDate start = from == null ? end.minusDays(DEFAULT_HISTORY_DAYS) : from;
            Instant fromAt = start.atStartOfDay(zone).toInstant();
            Instant toAt = end.plusDays(1).atStartOfDay(zone).toInstant();
            Page<StockMovement> movements = movementRepository.search(
                    campusId,
                    fromAt,
                    toAt,
                    foodId,
                    type,
                    PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
            Map<Long, StockLot> lots = lotRepository
                    .findAllById(movements.getContent().stream()
                            .map(StockMovement::getLotId)
                            .collect(Collectors.toSet()))
                    .stream()
                    .collect(Collectors.toMap(StockLot::getId, Function.identity()));
            Map<Long, Food> foods = foods(movements.getContent().stream()
                    .map(StockMovement::getFoodId)
                    .toList());
            PageResponse<StockMovementResponse> response = PageResponse.from(
                    movements, m -> mapper.toResponse(m, lots.get(m.getLotId()), foods.get(m.getFoodId())));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockShortfallResponse> getShortfalls(CurrentUser currentUser, Long campusId) {
        String logPrefix = LOG_PREFIX + "|getShortfalls|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            List<StockDailyDemand> rows =
                    demandRepository.findByCampusIdAndShortfallQtyGreaterThanOrderByMealDateAscFoodIdAsc(
                            campusId, BigDecimal.ZERO);
            Map<Long, Food> foods =
                    foods(rows.stream().map(StockDailyDemand::getFoodId).toList());
            List<StockShortfallResponse> response = rows.stream()
                    .map(row -> mapper.toResponse(row, foods.get(row.getFoodId())))
                    .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void syncDay(Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|syncDay|campusId=" + campusId + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            lotRepository.lockCampus(campusId);
            Map<Long, BigDecimal> wanted = dryDemand(campusId, date);
            Map<Long, StockDailyDemand> rows = demandRepository.findByCampusIdAndMealDate(campusId, date).stream()
                    .collect(Collectors.toMap(StockDailyDemand::getFoodId, Function.identity()));
            Set<Long> foodIds = new TreeSet<>(wanted.keySet());
            foodIds.addAll(rows.keySet());
            for (Long foodId : foodIds) {
                applyDemand(campusId, date, foodId, rows.get(foodId), wanted.getOrDefault(foodId, BigDecimal.ZERO));
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int unsyncedFoodCount(Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|unsyncedFoodCount|campusId=" + campusId + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            Map<Long, BigDecimal> wanted = dryDemand(campusId, date);
            Map<Long, BigDecimal> stored = demandRepository.findByCampusIdAndMealDate(campusId, date).stream()
                    .collect(Collectors.toMap(StockDailyDemand::getFoodId, StockDailyDemand::getRequiredQty));
            Set<Long> foodIds = new TreeSet<>(wanted.keySet());
            foodIds.addAll(stored.keySet());
            int unsynced = (int) foodIds.stream()
                    .filter(id ->
                            wanted.getOrDefault(id, BigDecimal.ZERO).compareTo(stored.getOrDefault(id, BigDecimal.ZERO))
                                    != 0)
                    .count();
            log.info("{}|END", logPrefix);
            return unsynced;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void resyncDay(CurrentUser currentUser, Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|resyncDay|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            // Người nhập kho hoặc người xác nhận suất của điểm trường được vá (AUTH-20)
            if (!campusAccessPolicy.canEditCampus(currentUser, Permission.STOCK_MANAGE, campusId)
                    && !campusAccessPolicy.canEditCampus(currentUser, Permission.MEAL_COUNT_CONFIRM, campusId)) {
                throw GlobalException.of(ApiCode.STOCK_ACCESS_DENIED);
            }
            syncDay(campusId, date);
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void settleShortfalls(Long campusId, Collection<Long> foodIds, Long receiptId, Long userId) {
        String logPrefix = LOG_PREFIX + "|settleShortfalls|campusId=" + campusId + "|receiptId=" + receiptId;
        log.info("{}|START", logPrefix);
        try {
            lotRepository.lockCampus(campusId);
            for (Long foodId : foodIds) {
                List<StockDailyDemand> open =
                        demandRepository.findByCampusIdAndFoodIdAndShortfallQtyGreaterThanOrderByMealDateAscIdAsc(
                                campusId, foodId, BigDecimal.ZERO);
                for (StockDailyDemand row : open) {
                    BigDecimal taken = takeFromLots(
                            campusId,
                            foodId,
                            row.getMealDate(),
                            row.getShortfallQty(),
                            StockMovementType.SHORTFALL_DEDUCT,
                            receiptId,
                            userId);
                    if (taken.signum() == 0) {
                        break;
                    }
                    row.change(row.getRequiredQty(), row.getShortfallQty().subtract(taken));
                    demandRepository.save(row);
                }
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /**
     * NUT-11, P-38: chỉ ghi chênh lệch giữa nhu cầu mới và lần trước. Tăng ⇒ trừ lô hết hạn trước, thiếu thì ghi phần
     * thiếu (không chặn); giảm ⇒ bớt phần thiếu trước, rồi trả lại đúng lô đã trừ của ngày đó.
     */
    private void applyDemand(Long campusId, LocalDate date, Long foodId, StockDailyDemand existing, BigDecimal wanted) {
        BigDecimal current = existing == null ? BigDecimal.ZERO : existing.getRequiredQty();
        int direction = wanted.compareTo(current);
        if (direction == 0) {
            return;
        }
        StockDailyDemand row = existing == null ? StockDailyDemand.empty(campusId, date, foodId) : existing;
        BigDecimal shortfall = row.getShortfallQty();
        if (direction > 0) {
            // Lần đầu suất ngày được chốt là trừ; các lần sau (xác nhận lại) là điều chỉnh
            StockMovementType type = existing == null ? StockMovementType.DEDUCT : StockMovementType.ADJUST;
            BigDecimal need = wanted.subtract(current);
            BigDecimal taken = takeFromLots(campusId, foodId, date, need, type, null, null);
            shortfall = shortfall.add(need.subtract(taken));
        } else {
            BigDecimal release = current.subtract(wanted);
            BigDecimal fromShortfall = release.min(shortfall);
            shortfall = shortfall.subtract(fromShortfall);
            returnToLots(campusId, foodId, date, release.subtract(fromShortfall));
        }
        row.change(wanted, shortfall);
        demandRepository.save(row);
    }

    /** Trừ tối đa {@code amount} theo lô hết hạn trước; bỏ qua lô đã hết hạn trước ngày ăn. Trả về lượng đã trừ. */
    private BigDecimal takeFromLots(
            Long campusId,
            Long foodId,
            LocalDate mealDate,
            BigDecimal amount,
            StockMovementType type,
            Long receiptId,
            Long userId) {
        List<StockLot> lots = lotRepository.findByCampusIdAndFoodIdOrderByExpiryDateAscIdAsc(campusId, foodId).stream()
                .filter(lot -> !lot.getExpiryDate().isBefore(mealDate))
                .toList();
        Map<Long, BigDecimal> balances = balances(lots);
        BigDecimal remaining = amount;
        for (StockLot lot : lots) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal balance = balances.getOrDefault(lot.getId(), BigDecimal.ZERO);
            if (balance.signum() <= 0) {
                continue;
            }
            BigDecimal take = balance.min(remaining);
            movementRepository.save(StockMovement.of(lot, type, take.negate(), mealDate, receiptId, "", userId));
            remaining = remaining.subtract(take);
        }
        return amount.subtract(remaining);
    }

    /** Trả lại {@code amount} vào các lô đã bị trừ cho ngày ăn này, lô trừ gần nhất trước. */
    private void returnToLots(Long campusId, Long foodId, LocalDate mealDate, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return;
        }
        Map<Long, BigDecimal> takenByLot = new LinkedHashMap<>();
        movementRepository
                .findByCampusIdAndMealDateAndFoodIdOrderByIdDesc(campusId, mealDate, foodId)
                .forEach(m -> takenByLot.merge(m.getLotId(), m.getQuantity().negate(), BigDecimal::add));
        Map<Long, StockLot> lots = lotRepository.findAllById(takenByLot.keySet()).stream()
                .collect(Collectors.toMap(StockLot::getId, Function.identity()));
        BigDecimal remaining = amount;
        for (Map.Entry<Long, BigDecimal> entry : takenByLot.entrySet()) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (entry.getValue().signum() <= 0) {
                continue;
            }
            BigDecimal give = entry.getValue().min(remaining);
            movementRepository.save(StockMovement.of(
                    lots.get(entry.getKey()), StockMovementType.ADJUST, give, mealDate, null, "", null));
            remaining = remaining.subtract(give);
        }
    }

    /**
     * NUT-07: nhu cầu thực phẩm kho khô của điểm trường–ngày = Σ bữa × nhóm tuổi (suất đã chốt × gam/trẻ của món theo
     * nhóm tuổi) ÷ (1 − thải bỏ%), theo thực đơn tuần đã công bố. Suất thay thế tính theo món chuẩn (chưa có món thay
     * thế dị ứng).
     */
    private Map<Long, BigDecimal> dryDemand(Long campusId, LocalDate date) {
        MealPlanWeek week = weekRepository
                .findByWeekStart(date.with(DayOfWeek.MONDAY))
                .filter(MealPlanWeek::isPublished)
                .orElse(null);
        MealPlanDay day = week == null ? null : week.day(date).orElse(null);
        if (day == null || day.isOff()) {
            return Map.of();
        }
        List<MealCount> counts = mealCountRepository.findByCampusIdAndDate(campusId, date).stream()
                .filter(MealCount::isConfirmed)
                .toList();
        if (counts.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> ageGroupOfClass = new HashMap<>();
        for (ClassroomInfoResponse c :
                classroomService.findYearClassrooms(counts.getFirst().getSchoolYearId())) {
            if (c.ageGroupId() != null) {
                ageGroupOfClass.put(c.id(), c.ageGroupId());
            }
        }
        Map<Long, MenuBlock> blockOfAgeGroup = ageGroupService.getAgeGroups().stream()
                .collect(Collectors.toMap(AgeGroupDetailResponse::id, g -> MenuBlock.ofAgeGroup(g.ageToMonths())));
        Map<Servings, Integer> servings = new HashMap<>();
        for (MealCount count : counts) {
            for (MealCountLine line : count.getLines()) {
                Long ageGroupId = ageGroupOfClass.get(line.getClassroomId());
                // Dòng chờ xác nhận lại chưa phải số chốt: bếp và kho theo số đã xác nhận (NUT-03)
                if (line.isPending() || ageGroupId == null) {
                    continue;
                }
                servings.merge(
                        new Servings(count.getSession(), ageGroupId),
                        line.getNormal() + line.getSubstitute(),
                        Integer::sum);
            }
        }
        Map<Long, Dish> dishes =
                dishRepository
                        .findByIdIn(day.getItems().stream()
                                .map(MealPlanItem::getDishId)
                                .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Dish::getId, Function.identity()));
        Map<Long, BigDecimal> edible = new HashMap<>();
        Map<Long, Food> foodById = new HashMap<>();
        for (MealPlanItem item : day.getItems()) {
            Dish dish = dishes.get(item.getDishId());
            if (dish == null) {
                continue;
            }
            servings.forEach((key, portions) -> {
                if (key.session() != item.getSession()
                        || blockOfAgeGroup.get(key.ageGroupId()) != item.getMenuBlock()
                        || portions <= 0) {
                    return;
                }
                for (DishIngredient ingredient : dish.getIngredients()) {
                    Food food = ingredient.getFood();
                    if (!Objects.equals(ingredient.getAgeGroupId(), key.ageGroupId())
                            || food.getStorage() != FoodStorage.DRY) {
                        continue;
                    }
                    foodById.put(food.getId(), food);
                    edible.merge(
                            food.getId(),
                            ingredient.getGramsPerChild().multiply(BigDecimal.valueOf(portions)),
                            BigDecimal::add);
                }
            });
        }
        Map<Long, BigDecimal> purchase = new HashMap<>();
        edible.forEach((foodId, qty) -> {
            BigDecimal waste = foodById.get(foodId).getWastePct();
            BigDecimal usable = BigDecimal.ONE.subtract(waste.divide(HUNDRED, 6, RoundingMode.HALF_UP));
            purchase.put(foodId, qty.divide(usable, 2, RoundingMode.HALF_UP));
        });
        return purchase;
    }

    private Map<Long, BigDecimal> balances(List<StockLot> lots) {
        if (lots.isEmpty()) {
            return Map.of();
        }
        return movementRepository.sumByLotIds(lots.stream().map(StockLot::getId).toList()).stream()
                .collect(Collectors.toMap(
                        StockMovementRepository.LotBalance::getLotId, StockMovementRepository.LotBalance::getBalance));
    }

    private Map<Long, Food> foods(Collection<Long> ids) {
        return foodRepository.findAllById(Set.copyOf(ids)).stream()
                .collect(Collectors.toMap(Food::getId, Function.identity()));
    }
}
