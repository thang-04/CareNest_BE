package com.carenest.controller.meals;

import com.carenest.dto.meals.FreshCheckRequest;
import com.carenest.dto.meals.FreshCheckResponse;
import com.carenest.dto.meals.FreshForecastRequest;
import com.carenest.dto.meals.FreshForecastResponse;
import com.carenest.dto.meals.FreshForecastSummaryResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.FreshGoodsService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Hàng tươi › bản dự trù theo kỳ (NUT-07) và kiểm nhận 2 lần/ngày (NUT-12); ghi cần STOCK_MANAGE ở điểm trường. */
@RestController
@RequestMapping("/nutrition")
@RequiredArgsConstructor
@Slf4j
public class FreshGoodsController {
    private static final String LOG_PREFIX = "[FreshGoodsController]";

    private final FreshGoodsService freshGoodsService;

    @GetMapping("/fresh-forecasts")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<FreshForecastSummaryResponse>>> getForecasts(
            @RequestParam Long campusId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getForecasts|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        List<FreshForecastSummaryResponse> forecasts = freshGoodsService.getForecasts(currentUser, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get fresh forecasts success", forecasts);
    }

    @PostMapping("/fresh-forecasts/preview")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<FreshForecastResponse>> previewForecast(
            @Valid @RequestBody FreshForecastRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|previewForecast|userId=" + currentUser.userId() + "|campusId=" + request.campusId();
        log.info("{}|START", logPrefix);
        FreshForecastResponse forecast = freshGoodsService.previewForecast(currentUser, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Preview fresh forecast success", forecast);
    }

    @PostMapping("/fresh-forecasts")
    @PreAuthorize("@permissionGuard.has('STOCK_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJson<FreshForecastResponse>> createForecast(
            @Valid @RequestBody FreshForecastRequest request, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|createForecast|userId=" + currentUser.userId() + "|campusId=" + request.campusId();
        log.info("{}|START", logPrefix);
        FreshForecastResponse forecast = freshGoodsService.createForecast(currentUser, request);
        log.info("{}|forecastId={}|END", logPrefix, forecast.id());
        return ResponseJson.toJsonWithData(ApiCode.CREATED, "Create fresh forecast success", forecast);
    }

    @GetMapping("/fresh-forecasts/{forecastId}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<FreshForecastResponse>> getForecast(
            @PathVariable Long forecastId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getForecast|userId=" + currentUser.userId() + "|forecastId=" + forecastId;
        log.info("{}|START", logPrefix);
        FreshForecastResponse forecast = freshGoodsService.getForecast(currentUser, forecastId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get fresh forecast success", forecast);
    }

    @DeleteMapping("/fresh-forecasts/{forecastId}")
    @PreAuthorize("@permissionGuard.has('STOCK_MANAGE')")
    public ResponseEntity<ResponseJson<Void>> deleteForecast(@PathVariable Long forecastId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|deleteForecast|userId=" + currentUser.userId() + "|forecastId=" + forecastId;
        log.info("{}|START", logPrefix);
        freshGoodsService.deleteForecast(currentUser, forecastId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJson(ApiCode.SUCCESSFUL, "Delete fresh forecast success");
    }

    @GetMapping("/fresh-checks")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<FreshCheckResponse>> getChecks(
            @RequestParam Long campusId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getChecks|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        FreshCheckResponse checks = freshGoodsService.getChecks(currentUser, campusId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get fresh checks success", checks);
    }

    @PutMapping("/fresh-checks")
    @PreAuthorize("@permissionGuard.has('STOCK_MANAGE')")
    public ResponseEntity<ResponseJson<FreshCheckResponse>> recordCheck(
            @RequestParam Long campusId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @Min(1) @Max(2) int round,
            @Valid @RequestBody FreshCheckRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|recordCheck|userId=" + currentUser.userId() + "|campusId=" + campusId
                + "|round=" + round;
        log.info("{}|START", logPrefix);
        FreshCheckResponse checks = freshGoodsService.recordCheck(currentUser, campusId, date, round, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Record fresh check success", checks);
    }
}
