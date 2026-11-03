package com.carenest.service.meals.impl;

import com.carenest.dto.meals.DayBoardResponse;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.entity.meals.MealCount;
import com.carenest.entity.meals.MealPlanDay;
import com.carenest.entity.meals.MealPlanWeek;
import com.carenest.entity.meals.StockDailyDemand;
import com.carenest.repository.meals.MealCountRepository;
import com.carenest.repository.meals.MealPlanWeekRepository;
import com.carenest.repository.meals.StockDailyDemandRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.service.meals.DayBoardService;
import com.carenest.service.meals.FoodSampleService;
import com.carenest.service.meals.FreshGoodsService;
import com.carenest.service.meals.StockLedgerService;
import com.carenest.service.organization.CampusService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DayBoardServiceImpl implements DayBoardService {
    private static final String LOG_PREFIX = "[DayBoardServiceImpl]";
    private static final String NONE = "NONE";

    private final MealPlanWeekRepository weekRepository;
    private final MealCountRepository mealCountRepository;
    private final StockDailyDemandRepository demandRepository;
    private final StockLedgerService stockLedgerService;
    private final FreshGoodsService freshGoodsService;
    private final FoodSampleService foodSampleService;
    private final CampusService campusService;
    private final CampusAccessPolicy campusAccessPolicy;
    private final Clock clock;

    @Override
    public DayBoardResponse getDayBoard(CurrentUser currentUser, LocalDate day, Long campusId) {
        String logPrefix = LOG_PREFIX + "|getDayBoard|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate date = day == null ? LocalDate.now(clock) : day;
            if (campusId != null) {
                campusAccessPolicy.checkCanReadCampus(currentUser, campusId);
            }
            LocalDate weekStart = date.with(DayOfWeek.MONDAY);
            Optional<MealPlanWeek> week = weekRepository.findByWeekStart(weekStart);
            Optional<MealPlanWeek> nextWeek = weekRepository.findByWeekStart(weekStart.plusWeeks(1));
            boolean dayOff = isDayOff(date, week);
            DayBoardResponse.WeekMenu menu = new DayBoardResponse.WeekMenu(
                    weekStart,
                    week.map(w -> w.getStatus().name()).orElse(NONE),
                    dayOff,
                    weekStart.plusWeeks(1),
                    nextWeek.map(w -> w.getStatus().name()).orElse(NONE));
            boolean published = week.map(MealPlanWeek::isPublished).orElse(false);
            List<DayBoardResponse.CampusBoard> boards = campusService.getCampuses(currentUser, null).stream()
                    .filter(c -> campusId == null || c.id().equals(campusId))
                    .map(c -> board(c, date, menu, published))
                    .toList();
            DayBoardResponse response = new DayBoardResponse(date, boards);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private DayBoardResponse.CampusBoard board(
            CampusResponse campus, LocalDate date, DayBoardResponse.WeekMenu menu, boolean published) {
        List<MealCount> counts = mealCountRepository.findByCampusIdAndDate(campus.id(), date).stream()
                .sorted(Comparator.comparing(MealCount::getSession))
                .toList();
        DayBoardResponse.MealCounts mealCounts = mealCounts(counts);
        FoodSampleService.ApprovalStatus samples = foodSampleService.getApprovalStatus(campus.id(), date);
        return new DayBoardResponse.CampusBoard(
                campus.id(),
                campus.name(),
                menu,
                mealCounts,
                dryStock(campus.id(), date, menu.dayOff(), published, mealCounts.status()),
                freshGoodsService.getReceiptStatus(campus.id(), date),
                samples.status(),
                samples.pendingCount());
    }

    /** Số suất người duyệt thấy: số chờ xác nhận lại nếu có, không thì số đã chốt (NUT-03). */
    private static DayBoardResponse.MealCounts mealCounts(List<MealCount> counts) {
        if (counts.isEmpty()) {
            return new DayBoardResponse.MealCounts(NONE, 0, List.of());
        }
        String status;
        if (counts.stream().anyMatch(c -> c.isConfirmed() && c.hasPending())) {
            status = "NEEDS_RECONFIRMATION";
        } else if (counts.stream().allMatch(MealCount::isConfirmed)) {
            status = "CONFIRMED";
        } else {
            status = "PENDING_CONFIRMATION";
        }
        List<DayBoardResponse.SessionServings> sessions = counts.stream()
                .map(c -> new DayBoardResponse.SessionServings(
                        c.getSession(),
                        c.visibleLines(true).stream()
                                .mapToInt(l -> l.getNormal() + l.getSubstitute())
                                .sum()))
                .toList();
        int total = sessions.stream()
                .mapToInt(DayBoardResponse.SessionServings::servings)
                .sum();
        return new DayBoardResponse.MealCounts(status, total, sessions);
    }

    private DayBoardResponse.DryStock dryStock(
            Long campusId, LocalDate date, boolean dayOff, boolean published, String countStatus) {
        int openShortfalls = demandRepository
                .findByCampusIdAndShortfallQtyGreaterThanOrderByMealDateAscFoodIdAsc(campusId, BigDecimal.ZERO)
                .size();
        String status;
        int foodCount = 0;
        int shortfallFoods = 0;
        if (dayOff) {
            status = "DAY_OFF";
        } else if (!published) {
            status = "NO_MENU";
        } else if (NONE.equals(countStatus) || "PENDING_CONFIRMATION".equals(countStatus)) {
            status = "WAITING_COUNT";
        } else {
            // Trừ kho sau commit từng lỗi: báo lệch, không để "không có thực phẩm khô" che phần chưa trừ
            int unsynced = stockLedgerService.unsyncedFoodCount(campusId, date);
            if (unsynced > 0) {
                status = "NOT_SYNCED";
                foodCount = unsynced;
            } else {
                List<StockDailyDemand> demands = demandRepository.findByCampusIdAndMealDate(campusId, date).stream()
                        .filter(d -> d.getRequiredQty().signum() > 0)
                        .toList();
                foodCount = demands.size();
                shortfallFoods = (int) demands.stream()
                        .filter(d -> d.getShortfallQty().signum() > 0)
                        .count();
                status = demands.isEmpty() ? "NOTHING_TO_DEDUCT" : shortfallFoods > 0 ? "SHORTFALL" : "DEDUCTED";
            }
        }
        return new DayBoardResponse.DryStock(status, foodCount, shortfallFoods, openShortfalls);
    }

    private static boolean isDayOff(LocalDate date, Optional<MealPlanWeek> week) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return true;
        }
        return week.flatMap(w -> w.day(date)).map(MealPlanDay::isOff).orElse(false);
    }
}
