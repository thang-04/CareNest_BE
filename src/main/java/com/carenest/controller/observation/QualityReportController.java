package com.carenest.controller.observation;

import com.carenest.dto.observation.QualityReportResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.observation.QualityReportService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Báo cáo chất lượng tháng theo sổ chỉ đạo chuyên môn (mục 4.1 + 4.2) cho PHT (điểm trường mình) và HT (toàn trường). */
@RestController
@RequiredArgsConstructor
@Slf4j
public class QualityReportController {
    private static final String LOG_PREFIX = "[QualityReportController]";

    private final QualityReportService qualityReportService;

    @GetMapping("/assessment/quality-report")
    @PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL')")
    public ResponseEntity<ResponseJson<QualityReportResponse>> getReport(
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Long campusId,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getReport|userId=" + currentUser.userId() + "|month=" + month;
        log.info("{}|START", logPrefix);
        QualityReportResponse report = qualityReportService.getReport(currentUser, schoolYearId, month, campusId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get quality report success", report);
    }
}
