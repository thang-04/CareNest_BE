package com.carenest.controller.meals;

import com.carenest.dto.meals.DayBoardResponse;
import com.carenest.dto.meals.MealPlanRequest;
import com.carenest.dto.meals.MealPlanResponse;
import com.carenest.dto.meals.MealPlanSummaryResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.DayBoardService;
import com.carenest.service.meals.MealPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thực đơn & dinh dưỡng › Kế hoạch tuần & công bố (NUT-08) và trang Bán trú hôm nay. */
@RestController
@RequestMapping("/nutrition")
@RequiredArgsConstructor
@Slf4j
public class MealPlanController {
    private static final String LOG_PREFIX = "[MealPlanController]";

    private final MealPlanService mealPlanService;
    private final DayBoardService dayBoardService;

    @GetMapping("/meal-plans")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<MealPlanSummaryResponse>>> getMealPlans(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMealPlans|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<MealPlanSummaryResponse> plans = mealPlanService.getMealPlans(currentUser, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get meal plans success", plans);
    }

    @GetMapping("/meal-plans/{weekStart}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<MealPlanResponse>> getMealPlan(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMealPlan|userId=" + currentUser.userId() + "|weekStart=" + weekStart;
        log.info("{}|START", logPrefix);
        MealPlanResponse plan = mealPlanService.getMealPlan(currentUser, weekStart);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get meal plan success", plan);
    }

    @PutMapping("/meal-plans/{weekStart}")
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MealPlanResponse>> saveMealPlan(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @Valid @RequestBody MealPlanRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveMealPlan|userId=" + currentUser.userId() + "|weekStart=" + weekStart;
        log.info("{}|START", logPrefix);
        MealPlanResponse plan = mealPlanService.saveMealPlan(currentUser, weekStart, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save meal plan success", plan);
    }

    @PostMapping("/meal-plans/{weekStart}/publish")
    @PreAuthorize("@permissionGuard.has('MENU_MANAGE')")
    public ResponseEntity<ResponseJson<MealPlanResponse>> publishMealPlan(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|publishMealPlan|userId=" + currentUser.userId() + "|weekStart=" + weekStart;
        log.info("{}|START", logPrefix);
        MealPlanResponse plan = mealPlanService.publishMealPlan(currentUser, weekStart);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Publish meal plan success", plan);
    }

    @GetMapping("/day-board")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<DayBoardResponse>> getDayBoard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long campusId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDayBoard|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        DayBoardResponse board = dayBoardService.getDayBoard(currentUser, date, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get day board success", board);
    }
}
