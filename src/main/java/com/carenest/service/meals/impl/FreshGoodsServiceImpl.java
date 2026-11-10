package com.carenest.service.meals.impl;

import com.carenest.dto.meals.FreshCheckRequest;
import com.carenest.dto.meals.FreshCheckResponse;
import com.carenest.dto.meals.FreshForecastRequest;
import com.carenest.dto.meals.FreshForecastResponse;
import com.carenest.dto.meals.FreshForecastSummaryResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.entity.meals.Dish;
import com.carenest.entity.meals.Food;
import com.carenest.entity.meals.FreshCheck;
import com.carenest.entity.meals.FreshCheckLine;
import com.carenest.entity.meals.FreshForecast;
import com.carenest.entity.meals.FreshForecastCount;
import com.carenest.entity.meals.FreshForecastLine;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.meals.StockMapper;
import com.carenest.repository.meals.FoodRepository;
import com.carenest.repository.meals.FreshCheckRepository;
import com.carenest.repository.meals.FreshForecastRepository;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.meals.FreshGoodsService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FoodStorage;
import com.carenest.utils.enums.FreshCheckSource;
import com.carenest.utils.enums.MenuBlock;
import com.carenest.utils.enums.Permission;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FreshGoodsServiceImpl implements FreshGoodsService {
    private static final String LOG_PREFIX = "[FreshGoodsServiceImpl]";
    private static final String NONE = "NONE";
    private static final int MAX_PERIOD_DAYS = 31;
    private static final int ROUND_FORECAST = 1;
    private static final int ROUND_SERVINGS = 2;
    private static final String NO_FORECAST_REASON = "Chưa có bản dự trù hàng tươi cho ngày này";
    private static final String DAY_OFF_REASON = "Ngày nghỉ, không phục vụ bữa";
    private static final String MENU_NOT_PUBLISHED_REASON = "Thực đơn tuần chưa công bố";
    private static final String COUNT_NOT_CONFIRMED_REASON = "Suất ăn của ngày chưa được xác nhận";

    private final FreshForecastRepository forecastRepository;
    private final FreshCheckRepository checkRepository;
    private final MealPlanWeekRepository weekRepository;
    private final MealCountRepository mealCountRepository;
    private final FoodRepository foodRepository;
    private final MealDemandCalculator demandCalculator;
    private final DryStockOutlook dryStockOutlook;
    private final AgeGroupService ageGroupService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final StockMapper stockMapper;
    private final Clock clock;

    /** Một ngày của kỳ: {@code day} null khi nghỉ hoặc tuần chưa có thực đơn. */
    private record DayInfo(LocalDate date, boolean off, String planStatus, MealPlanDay day) {}

    private record Calculated(FreshForecast forecast, List<DayInfo> days, List<AgeGroupDetailResponse> ageGroups) {}

    /** Căn cứ tính lượng cần nhận lần 2: ngày nghỉ, lý do chưa kiểm được (null = được), ngày thực đơn và sổ báo ăn. */
    private record ConfirmedBasis(boolean dayOff, String blockedReason, MealPlanDay day, List<MealCount> counts) {}

    /** Lượng cần nhận của một lần kiểm: foodId → lượng (đơn vị gốc). */
    private record Expected(
            boolean available, String reason, FreshCheckSource source, Long forecastId, Map<Long, BigDecimal> lines) {

        static Expected unavailable(FreshCheckSource source, String reason) {
            return new Expected(false, reason, source, null, Map.of());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FreshForecastSummaryResponse> getForecasts(CurrentUser currentUser, Long campusId) {
        String logPrefix = LOG_PREFIX + "|getForecasts|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            List<FreshForecast> forecasts = forecastRepository.findByCampusIdOrderByCreatedAtDescIdDesc(campusId);
            Map<Long, Long> foodCounts = forecasts.isEmpty()
                    ? Map.of()
                    : forecastRepository
                            .countFoods(
                                    forecasts.stream().map(FreshForecast::getId).toList())
                            .stream()
                            .collect(Collectors.toMap(
                                    FreshForecastRepository.FoodCount::getForecastId,
                                    FreshForecastRepository.FoodCount::getFoodCount));
            List<FreshForecastSummaryResponse> response = forecasts.stream()
                    .map(f -> new FreshForecastSummaryResponse(
                            f.getId(),
                            f.getCampusId(),
                            f.getPeriodFrom(),
                            f.getPeriodTo(),
                            f.getNote(),
                            foodCounts.getOrDefault(f.getId(), 0L).intValue(),
                            f.getCreatedAt()))
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
    public FreshForecastResponse previewForecast(CurrentUser currentUser, FreshForecastRequest request) {
        String logPrefix =
                LOG_PREFIX + "|previewForecast|userId=" + currentUser.userId() + "|campusId=" + request.campusId();
        log.info("{}|START", logPrefix);
        try {
            campusAccessPolicy.checkCanReadCampus(currentUser, request.campusId());
            requireCampus(request.campusId());
            Calculated calculated = calculate(request, currentUser.userId());
            FreshForecastResponse response = toResponse(currentUser, calculated);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FreshForecastResponse createForecast(CurrentUser currentUser, FreshForecastRequest request) {
        String logPrefix =
                LOG_PREFIX + "|createForecast|userId=" + currentUser.userId() + "|campusId=" + request.campusId();
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser, request.campusId());
            requireCampus(request.campusId());
            Calculated calculated = calculate(request, currentUser.userId());
            FreshForecast saved = forecastRepository.save(calculated.forecast());
            FreshForecastResponse response =
                    toResponse(currentUser, new Calculated(saved, calculated.days(), calculated.ageGroups()));
            log.info("{}|forecastId={}|END", logPrefix, saved.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FreshForecastResponse getForecast(CurrentUser currentUser, Long forecastId) {
        String logPrefix = LOG_PREFIX + "|getForecast|userId=" + currentUser.userId() + "|forecastId=" + forecastId;
        log.info("{}|START", logPrefix);
        try {
            FreshForecast forecast = forecastRepository
                    .findWithLinesById(forecastId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.FRESH_FORECAST_NOT_FOUND));
            campusAccessPolicy.checkCanReadCampus(currentUser, forecast.getCampusId());
            FreshForecastResponse response = toResponse(
                    currentUser,
                    new Calculated(
                            forecast,
                            planDays(forecast.getPeriodFrom(), forecast.getPeriodTo()),
                            ageGroupService.getAgeGroups()));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteForecast(CurrentUser currentUser, Long forecastId) {
        String logPrefix = LOG_PREFIX + "|deleteForecast|userId=" + currentUser.userId() + "|forecastId=" + forecastId;
        log.info("{}|START", logPrefix);
        try {
            FreshForecast forecast = forecastRepository
                    .findById(forecastId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.FRESH_FORECAST_NOT_FOUND));
            checkCanManage(currentUser, forecast.getCampusId());
            // Lịch sử kiểm nhận giữ nguyên: khóa ngoại forecast_id tự về null
            forecastRepository.delete(forecast);
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FreshCheckResponse getChecks(CurrentUser currentUser, Long campusId, LocalDate date) {
        String logPrefix =
                LOG_PREFIX + "|getChecks|userId=" + currentUser.userId() + "|campusId=" + campusId + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            requireCampus(campusId);
            FreshCheckResponse response = buildChecks(currentUser, campusId, date);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public FreshCheckResponse recordCheck(
            CurrentUser currentUser, Long campusId, LocalDate date, int round, FreshCheckRequest request) {
        String logPrefix = LOG_PREFIX + "|recordCheck|userId=" + currentUser.userId() + "|campusId=" + campusId
                + "|date=" + date + "|round=" + round;
        log.info("{}|START", logPrefix);
        try {
            checkCanManage(currentUser, campusId);
            requireCampus(campusId);
            // Ghi cùng (điểm trường, ngày, lần) chạy tuần tự: bấm lưu hai lần không đụng khóa duy nhất
            checkRepository.lockCampus(campusId);
            Expected expected =
                    switch (round) {
                        case ROUND_FORECAST -> forecastExpected(campusId, date);
                        case ROUND_SERVINGS -> servingsExpected(campusId, date);
                        default -> throw GlobalException.of(ApiCode.BAD_REQUEST);
                    };
            // NUT-12: lần 1 cần bản dự trù, lần 2 cần thực đơn công bố và suất đã xác nhận
            if (!expected.available()) {
                throw GlobalException.of(ApiCode.FRESH_CHECK_NOT_AVAILABLE, round, expected.reason());
            }
            validateLines(expected, request.lines());
            FreshCheck check = checkRepository
                    .findByCampusIdAndCheckDateAndRound(campusId, date, (short) round)
                    .orElseGet(() -> FreshCheck.empty(campusId, date, round));
            String note = request.note() == null ? "" : request.note().trim();
            check.record(
                    expected.forecastId(),
                    currentUser.userId(),
                    clock.instant(),
                    note,
                    request.lines().stream()
                            .map(line -> FreshCheckLine.of(
                                    line.foodId(),
                                    expected.lines().get(line.foodId()),
                                    line.receivedQty(),
                                    line.result(),
                                    line.note() == null ? "" : line.note().trim()))
                            .toList());
            checkRepository.saveAndFlush(check);
            FreshCheckResponse response = buildChecks(currentUser, campusId, date);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String getReceiptStatus(Long campusId, LocalDate date) {
        String logPrefix = LOG_PREFIX + "|getReceiptStatus|campusId=" + campusId + "|date=" + date;
        log.info("{}|START", logPrefix);
        try {
            ConfirmedBasis basis = confirmedBasis(campusId, date);
            String status;
            if (basis.dayOff()) {
                status = "DAY_OFF";
            } else {
                Set<Integer> recorded = checkRepository.findByCampusIdAndCheckDate(campusId, date).stream()
                        .map(check -> (int) check.getRound())
                        .collect(Collectors.toSet());
                if (!recorded.contains(ROUND_FORECAST)) {
                    status = forecastRepository.findCovering(campusId, date).isEmpty()
                            ? "NO_FORECAST"
                            : "ROUND1_PENDING";
                } else if (recorded.contains(ROUND_SERVINGS)) {
                    status = "DONE";
                } else {
                    status = basis.blockedReason() == null ? "ROUND2_PENDING" : "ROUND1_DONE";
                }
            }
            log.info("{}|END", logPrefix);
            return status;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // AUTH-20: STOCK_MANAGE theo điểm trường (HT mọi điểm trường; PHT điểm trường được giao hoặc cả trường)
    private void checkCanManage(CurrentUser currentUser, Long campusId) {
        if (!campusAccessPolicy.canEditCampus(currentUser, Permission.STOCK_MANAGE, campusId)) {
            throw GlobalException.of(ApiCode.STOCK_ACCESS_DENIED);
        }
    }

    private void requireCampus(Long campusId) {
        if (!forecastRepository.campusExists(campusId)) {
            throw GlobalException.of(ApiCode.CAMPUS_NOT_FOUND);
        }
    }

    /**
     * NUT-07: sĩ số dự kiến × bữa của nhóm tuổi phục vụ ⇒ suất; nhu cầu hàng tươi từng ngày của thực đơn tuần (nháp hoặc
     * đã công bố) trong kỳ, bỏ ngày nghỉ. Nhóm tuổi không gửi số dự kiến lấy theo sĩ số đang học.
     */
    private Calculated calculate(FreshForecastRequest request, Long userId) {
        LocalDate from = request.periodFrom();
        LocalDate to = request.periodTo();
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
            throw GlobalException.of(ApiCode.FRESH_FORECAST_PERIOD_INVALID);
        }
        List<AgeGroupDetailResponse> ageGroups = ageGroupService.getAgeGroups();
        Map<Long, Integer> enrolled = demandCalculator.enrolledByAgeGroup(request.campusId(), from);
        Map<Long, Integer> requested = requestedCounts(request.counts(), ageGroups);
        List<FreshForecastCount> counts = new ArrayList<>();
        List<MealDemandCalculator.Servings> servings = new ArrayList<>();
        for (AgeGroupDetailResponse group : ageGroups) {
            if (!enrolled.containsKey(group.id()) && !requested.containsKey(group.id())) {
                continue;
            }
            int enrolledCount = enrolled.getOrDefault(group.id(), 0);
            int expectedCount = requested.getOrDefault(group.id(), enrolledCount);
            counts.add(FreshForecastCount.of(group.id(), enrolledCount, expectedCount));
            if (expectedCount > 0 && group.mealSessions() != null) {
                group.mealSessions()
                        .forEach(session ->
                                servings.add(new MealDemandCalculator.Servings(session, group.id(), expectedCount)));
            }
        }
        List<DayInfo> days = planDays(from, to);
        String note = request.note() == null ? "" : request.note().trim();
        FreshForecast forecast =
                FreshForecast.of(request.campusId(), from, to, note, counts, forecastLines(days, servings), userId);
        return new Calculated(forecast, days, ageGroups);
    }

    private static Map<Long, Integer> requestedCounts(
            List<FreshForecastRequest.FreshForecastCountInput> inputs, List<AgeGroupDetailResponse> ageGroups) {
        Map<Long, Integer> requested = new HashMap<>();
        if (inputs == null) {
            return requested;
        }
        Set<Long> known = ageGroups.stream().map(AgeGroupDetailResponse::id).collect(Collectors.toSet());
        for (FreshForecastRequest.FreshForecastCountInput input : inputs) {
            // Nhóm tuổi lạ hoặc gửi trùng đều không dùng được
            if (!known.contains(input.ageGroupId())
                    || requested.putIfAbsent(input.ageGroupId(), input.expectedCount()) != null) {
                throw GlobalException.of(ApiCode.AGE_GROUP_INVALID);
            }
        }
        return requested;
    }

    /** Mọi ngày của kỳ kèm thực đơn tuần chứa ngày; thứ Bảy, Chủ nhật và ngày nghỉ của tuần là ngày nghỉ. */
    private List<DayInfo> planDays(LocalDate from, LocalDate to) {
        Map<LocalDate, MealPlanWeek> weeks = weekRepository
                .findWithDaysByWeekStartBetweenOrderByWeekStartAsc(
                        from.with(DayOfWeek.MONDAY), to.with(DayOfWeek.MONDAY))
                .stream()
                .collect(Collectors.toMap(MealPlanWeek::getWeekStart, Function.identity()));
        List<DayInfo> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            MealPlanWeek week = weeks.get(date.with(DayOfWeek.MONDAY));
            MealPlanDay day = week == null ? null : week.day(date).orElse(null);
            boolean off = isWeekend(date) || (day != null && day.isOff());
            days.add(new DayInfo(
                    date, off, week == null ? NONE : week.getStatus().name(), off ? null : day));
        }
        return days;
    }

    private List<FreshForecastLine> forecastLines(List<DayInfo> days, List<MealDemandCalculator.Servings> servings) {
        List<MealPlanDay> planDays =
                days.stream().map(DayInfo::day).filter(Objects::nonNull).toList();
        if (planDays.isEmpty() || servings.isEmpty()) {
            return List.of();
        }
        Map<Long, Dish> dishes = demandCalculator.dishesOf(planDays);
        Map<Long, MenuBlock> blocks = demandCalculator.blockOfAgeGroup();
        Map<LocalDate, Map<Long, BigDecimal>> demandByDate = new LinkedHashMap<>();
        for (DayInfo info : days) {
            if (info.day() != null) {
                demandByDate.put(
                        info.date(),
                        MealDemandCalculator.compute(info.day(), dishes, blocks, servings, FoodStorage.FRESH));
            }
        }
        Map<Long, Food> foods = foods(demandByDate.values().stream()
                .flatMap(byFood -> byFood.keySet().stream())
                .collect(Collectors.toSet()));
        // Hàng tươi giao theo ngày ⇒ làm tròn lên nguyên đơn vị mua cho từng ngày, tổng kỳ = cộng các ngày đã làm tròn
        List<FreshForecastLine> lines = new ArrayList<>();
        demandByDate.forEach((date, byFood) -> byFood.entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add(FreshForecastLine.of(
                        date, entry.getKey(), PurchaseUnits.roundUp(entry.getValue(), foods.get(entry.getKey()))))));
        return lines;
    }

    private FreshForecastResponse toResponse(CurrentUser currentUser, Calculated calculated) {
        FreshForecast forecast = calculated.forecast();
        Map<Long, String> ageGroupNames = calculated.ageGroups().stream()
                .collect(Collectors.toMap(AgeGroupDetailResponse::id, AgeGroupDetailResponse::name));
        Map<Long, Food> foods = foods(
                forecast.getLines().stream().map(FreshForecastLine::getFoodId).collect(Collectors.toSet()));
        Map<Long, List<FreshForecastResponse.FreshForecastDayQty>> byFood = new LinkedHashMap<>();
        for (FreshForecastLine line : forecast.getLines()) {
            byFood.computeIfAbsent(line.getFoodId(), id -> new ArrayList<>())
                    .add(new FreshForecastResponse.FreshForecastDayQty(line.getMenuDate(), line.getQuantity()));
        }
        List<FreshForecastResponse.FreshForecastLine> lines = byFood.entrySet().stream()
                .map(entry -> new FreshForecastResponse.FreshForecastLine(
                        stockMapper.toFoodRef(foods.get(entry.getKey())),
                        entry.getValue().stream()
                                .map(FreshForecastResponse.FreshForecastDayQty::quantity)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        entry.getValue()))
                .sorted(Comparator.comparing((FreshForecastResponse.FreshForecastLine line) ->
                        line.food().name()))
                .toList();
        return new FreshForecastResponse(
                forecast.getId(),
                forecast.getCampusId(),
                forecast.getPeriodFrom(),
                forecast.getPeriodTo(),
                forecast.getNote(),
                forecast.getCounts().stream()
                        .map(count -> new FreshForecastResponse.FreshForecastCount(
                                count.ageGroupId(),
                                ageGroupNames.get(count.ageGroupId()),
                                count.enrolledCount(),
                                count.expectedCount()))
                        .toList(),
                calculated.days().stream()
                        .map(day -> new FreshForecastResponse.FreshForecastDay(day.date(), day.off(), day.planStatus()))
                        .toList(),
                lines,
                dryLines(forecast),
                forecast.getCreatedAt(),
                forecast.getCreatedBy(),
                campusAccessPolicy.canEditCampus(currentUser, Permission.STOCK_MANAGE, forecast.getCampusId()));
    }

    /**
     * NUT-11, P-24 (user đổi 08/11/2026): hàng khô cần nhập thêm tới hết kỳ. Ngày trong kỳ dùng số dự kiến của chính bản
     * dự trù; ngày chưa chốt trước kỳ vẫn tiêu tồn, tính theo luật mặc định của {@link DryStockOutlook}.
     */
    private List<FreshForecastResponse.DryLine> dryLines(FreshForecast forecast) {
        Map<Long, Integer> counts = forecast.getCounts().stream()
                .collect(Collectors.toMap(
                        FreshForecastCount::ageGroupId, FreshForecastCount::expectedCount, Integer::sum));
        List<MealDemandCalculator.Servings> servings = demandCalculator.expectedServings(counts);
        List<DryStockOutlook.Line> shortages = dryStockOutlook.shortages(
                forecast.getCampusId(),
                forecast.getPeriodTo(),
                date -> date.isBefore(forecast.getPeriodFrom()) ? null : servings);
        Map<Long, Food> foods =
                foods(shortages.stream().map(DryStockOutlook.Line::foodId).collect(Collectors.toSet()));
        return shortages.stream()
                .map(line -> new FreshForecastResponse.DryLine(
                        stockMapper.toFoodRef(foods.get(line.foodId())),
                        line.requiredQty(),
                        line.availableQty(),
                        PurchaseUnits.roundUp(line.shortQty(), foods.get(line.foodId())),
                        line.shortFrom()))
                .toList();
    }

    /** Lần 1 (NUT-12): lượng cần nhận = dòng của ngày trong bản dự trù mới lập nhất có kỳ chứa ngày. */
    private Expected forecastExpected(Long campusId, LocalDate date) {
        FreshForecast forecast = forecastRepository.findCovering(campusId, date).stream()
                .findFirst()
                .orElse(null);
        if (forecast == null) {
            return Expected.unavailable(FreshCheckSource.FORECAST, NO_FORECAST_REASON);
        }
        Map<Long, BigDecimal> lines = new LinkedHashMap<>();
        forecast.getLines().stream()
                .filter(line -> line.getMenuDate().equals(date))
                .forEach(line -> lines.merge(line.getFoodId(), line.getQuantity(), BigDecimal::add));
        return new Expected(true, null, FreshCheckSource.FORECAST, forecast.getId(), lines);
    }

    /**
     * Lần 2 (NUT-12): lượng cần nhận = nhu cầu hàng tươi theo suất đã chốt, cùng công thức trừ kho khô; đơn vị mua đếm
     * được làm tròn lên nguyên đơn vị như bản dự trù.
     */
    private Expected servingsExpected(Long campusId, LocalDate date) {
        ConfirmedBasis basis = confirmedBasis(campusId, date);
        if (basis.blockedReason() != null) {
            return Expected.unavailable(FreshCheckSource.CONFIRMED_SERVINGS, basis.blockedReason());
        }
        Map<Long, BigDecimal> demand = demandCalculator.demand(
                basis.day(), demandCalculator.confirmedServings(basis.counts()), FoodStorage.FRESH);
        Map<Long, Food> foods = foods(demand.keySet());
        Map<Long, BigDecimal> lines = new LinkedHashMap<>();
        demand.entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry ->
                        lines.put(entry.getKey(), PurchaseUnits.roundUp(entry.getValue(), foods.get(entry.getKey()))));
        return new Expected(true, null, FreshCheckSource.CONFIRMED_SERVINGS, null, lines);
    }

    private ConfirmedBasis confirmedBasis(Long campusId, LocalDate date) {
        MealPlanWeek week =
                weekRepository.findByWeekStart(date.with(DayOfWeek.MONDAY)).orElse(null);
        MealPlanDay day = week == null ? null : week.day(date).orElse(null);
        if (isWeekend(date) || (day != null && day.isOff())) {
            return new ConfirmedBasis(true, DAY_OFF_REASON, null, List.of());
        }
        if (week == null || !week.isPublished() || day == null) {
            return new ConfirmedBasis(false, MENU_NOT_PUBLISHED_REASON, null, List.of());
        }
        List<MealCount> counts = mealCountRepository.findByCampusIdAndDate(campusId, date);
        if (counts.isEmpty() || !counts.stream().allMatch(MealCount::isConfirmed)) {
            return new ConfirmedBasis(false, COUNT_NOT_CONFIRMED_REASON, null, List.of());
        }
        return new ConfirmedBasis(false, null, day, counts);
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** Đủ một dòng cho mỗi thực phẩm cần nhận: không thiếu, không thực phẩm lạ, không trùng. */
    private void validateLines(Expected expected, List<FreshCheckRequest.FreshCheckLineInput> lines) {
        Set<Long> sent = new HashSet<>();
        Set<Long> duplicated = new TreeSet<>();
        for (FreshCheckRequest.FreshCheckLineInput line : lines) {
            if (!sent.add(line.foodId())) {
                duplicated.add(line.foodId());
            }
        }
        Set<Long> missing = new TreeSet<>(expected.lines().keySet());
        missing.removeAll(sent);
        Set<Long> unknown = new TreeSet<>(sent);
        unknown.removeAll(expected.lines().keySet());
        if (missing.isEmpty() && unknown.isEmpty() && duplicated.isEmpty()) {
            return;
        }
        Set<Long> all = new HashSet<>(missing);
        all.addAll(unknown);
        all.addAll(duplicated);
        Map<Long, Food> foods = foods(all);
        List<String> problems = new ArrayList<>();
        if (!missing.isEmpty()) {
            problems.add("thiếu " + names(missing, foods));
        }
        if (!unknown.isEmpty()) {
            problems.add("không thuộc danh sách " + names(unknown, foods));
        }
        if (!duplicated.isEmpty()) {
            problems.add("trùng " + names(duplicated, foods));
        }
        throw GlobalException.of(ApiCode.FRESH_CHECK_LINES_MISMATCH, String.join("; ", problems));
    }

    private static String names(Collection<Long> ids, Map<Long, Food> foods) {
        return ids.stream()
                .map(id -> foods.containsKey(id) ? foods.get(id).getName() : "#" + id)
                .collect(Collectors.joining(", "));
    }

    private FreshCheckResponse buildChecks(CurrentUser currentUser, Long campusId, LocalDate date) {
        Map<Integer, FreshCheck> saved = checkRepository.findByCampusIdAndCheckDate(campusId, date).stream()
                .collect(Collectors.toMap(check -> (int) check.getRound(), Function.identity()));
        Expected forecastRound = forecastExpected(campusId, date);
        Expected servingsRound = servingsExpected(campusId, date);
        Set<Long> foodIds = new HashSet<>(forecastRound.lines().keySet());
        foodIds.addAll(servingsRound.lines().keySet());
        saved.values().forEach(check -> check.getLines().forEach(line -> foodIds.add(line.getFoodId())));
        Map<Long, Food> foods = foods(foodIds);
        return new FreshCheckResponse(
                campusId,
                date,
                campusAccessPolicy.canEditCampus(currentUser, Permission.STOCK_MANAGE, campusId),
                List.of(
                        toRound(ROUND_FORECAST, forecastRound, saved.get(ROUND_FORECAST), foods),
                        toRound(ROUND_SERVINGS, servingsRound, saved.get(ROUND_SERVINGS), foods)));
    }

    private FreshCheckResponse.FreshCheckRound toRound(
            int round, Expected expected, FreshCheck saved, Map<Long, Food> foods) {
        List<FreshCheckResponse.FreshCheckLine> lines = saved == null
                ? expected.lines().entrySet().stream()
                        .map(entry -> new FreshCheckResponse.FreshCheckLine(
                                stockMapper.toFoodRef(foods.get(entry.getKey())), entry.getValue(), null, null, null))
                        .toList()
                : saved.getLines().stream()
                        .map(line -> new FreshCheckResponse.FreshCheckLine(
                                stockMapper.toFoodRef(foods.get(line.getFoodId())),
                                line.getExpectedQty(),
                                line.getReceivedQty(),
                                line.getResult(),
                                line.getNote()))
                        .toList();
        return new FreshCheckResponse.FreshCheckRound(
                round,
                expected.available(),
                expected.reason(),
                expected.source(),
                saved == null ? expected.forecastId() : saved.getForecastId(),
                saved != null,
                saved == null ? null : saved.getCheckedAt(),
                saved == null ? null : saved.getCheckedBy(),
                saved == null ? null : saved.getNote(),
                lines.stream()
                        .sorted(Comparator.comparing((FreshCheckResponse.FreshCheckLine line) ->
                                line.food().name()))
                        .toList());
    }

    private Map<Long, Food> foods(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return foodRepository.findAllById(Set.copyOf(ids)).stream()
                .collect(Collectors.toMap(Food::getId, Function.identity()));
    }
}
