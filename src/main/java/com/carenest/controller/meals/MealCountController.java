package com.carenest.controller.meals;

import com.carenest.dto.meals.MealAdjustRequest;
import com.carenest.dto.meals.MealCountDayResponse;
import com.carenest.dto.meals.MealCountResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.MealCountService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sổ báo ăn: xem theo ngày, PHT điểm trường xác nhận từng bữa và điều chỉnh có lý do. Phạm vi kiểm ở service. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MealCountController {
    private static final String LOG_PREFIX = "[MealCountController]";

    private final MealCountService mealCountService;

    @GetMapping("/meal-counts")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'KITCHEN_STAFF')")
    public ResponseEntity<ResponseJson<MealCountDayResponse>> getMealCounts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long schoolYearId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getMealCounts|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        MealCountDayResponse day = mealCountService.getMealCounts(currentUser, date, schoolYearId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get meal counts success", day);
    }

    @PostMapping("/meal-counts/{mealCountId}/confirm")
    @PreAuthorize("hasRole('VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<MealCountResponse>> confirm(
            @PathVariable Long mealCountId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|confirm|userId=" + currentUser.userId() + "|mealCountId=" + mealCountId;
        log.info("{}|START", logPrefix);
        MealCountResponse count = mealCountService.confirm(currentUser, mealCountId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Confirm meal count success", count);
    }

    @PostMapping("/meal-counts/adjustments")
    @PreAuthorize("hasRole('VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<MealCountResponse>>> adjust(
            @Valid @RequestBody MealAdjustRequest request, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|adjust|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        List<MealCountResponse> counts = mealCountService.adjust(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Adjust meal count success", counts);
    }
}
