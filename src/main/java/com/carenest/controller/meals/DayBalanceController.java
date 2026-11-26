package com.carenest.controller.meals;

import com.carenest.dto.meals.DayBalanceRequest;
import com.carenest.dto.meals.DayBalanceResponse;
import com.carenest.dto.meals.DayBalanceStatusResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.DayBalanceService;
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

/** Thực đơn & dinh dưỡng › Cân đối khẩu phần theo ngày. */
@RestController
@RequestMapping("/nutrition/day-balances")
@RequiredArgsConstructor
@Slf4j
public class DayBalanceController {
    private static final String LOG_PREFIX = "[DayBalanceController]";

    private final DayBalanceService dayBalanceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<List<DayBalanceStatusResponse>>> getStatuses(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getStatuses|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<DayBalanceStatusResponse> statuses = dayBalanceService.getStatuses(currentUser, from, to);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get day balance statuses success", statuses);
    }

    @GetMapping("/{date}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<DayBalanceResponse>> getBalance(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int round,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getBalance|userId=" + currentUser.userId() + "|date=" + date;
        log.info("{}|START", logPrefix);
        DayBalanceResponse balance = dayBalanceService.getBalance(currentUser, date, round);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get day balance success", balance);
    }

    @PostMapping("/{date}/preview")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<DayBalanceResponse>> previewBalance(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int round,
            @Valid @RequestBody DayBalanceRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|previewBalance|userId=" + currentUser.userId() + "|date=" + date;
        log.info("{}|START", logPrefix);
        DayBalanceResponse balance = dayBalanceService.previewBalance(currentUser, date, round, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Preview day balance success", balance);
    }

    @PutMapping("/{date}")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<DayBalanceResponse>> saveBalance(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int round,
            @Valid @RequestBody DayBalanceRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|saveBalance|userId=" + currentUser.userId() + "|date=" + date;
        log.info("{}|START", logPrefix);
        DayBalanceResponse balance = dayBalanceService.saveBalance(currentUser, date, round, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Save day balance success", balance);
    }
}
