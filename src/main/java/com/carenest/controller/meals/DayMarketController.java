package com.carenest.controller.meals;

import com.carenest.dto.meals.DayMarketResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.DayMarketService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bảng đi chợ theo ngày: thực phẩm, tiền và thu–chi của một điểm trường (chỉ đọc). */
@RestController
@RequestMapping("/nutrition")
@RequiredArgsConstructor
@Slf4j
public class DayMarketController {
    private static final String LOG_PREFIX = "[DayMarketController]";

    private final DayMarketService dayMarketService;

    @GetMapping("/day-market")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<DayMarketResponse>> getDayMarket(
            @RequestParam Long campusId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getDayMarket|userId=" + currentUser.userId() + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        DayMarketResponse market = dayMarketService.getDayMarket(currentUser, campusId, date);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get day market success", market);
    }
}
