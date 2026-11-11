package com.carenest.controller.health;

import com.carenest.dto.health.HealthTrendAnalysisResponse;
import com.carenest.dto.health.HealthTrendApproveRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.health.HealthTrendService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Giải thích xu hướng sức khỏe (HLT-02..05, AI-07): nhân sự xem được trẻ phân tích, xem bản mới nhất, duyệt. Phạm vi trẻ
 * kiểm ở service. Không log nội dung.
 */
@RestController
@RequestMapping("/children/{childId}/health/trend-analysis")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER')")
public class HealthTrendController {
    private static final String LOG_PREFIX = "[HealthTrendController]";

    private final HealthTrendService healthTrendService;

    @PostMapping
    public ResponseEntity<ResponseJson<HealthTrendAnalysisResponse>> analyzeTrend(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|analyzeTrend|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        HealthTrendAnalysisResponse analysis = healthTrendService.analyze(currentUser, childId);
        log.info("{}|status={}|END", logPrefix, analysis.status());
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Analyze health trend success", analysis);
    }

    @GetMapping
    public ResponseEntity<ResponseJson<HealthTrendAnalysisResponse>> latestTrend(
            @PathVariable Long childId, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|latestTrend|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        HealthTrendAnalysisResponse analysis = healthTrendService.latest(currentUser, childId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get health trend success", analysis);
    }

    @PostMapping("/{interpretationId}/approve")
    public ResponseEntity<ResponseJson<HealthTrendAnalysisResponse>> approveTrend(
            @PathVariable Long childId,
            @PathVariable Long interpretationId,
            @RequestBody(required = false) HealthTrendApproveRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|approveTrend|userId=" + currentUser.userId() + "|childId=" + childId + "|id="
                + interpretationId;
        log.info("{}|START", logPrefix);
        HealthTrendAnalysisResponse analysis =
                healthTrendService.approve(currentUser, childId, interpretationId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Approve health trend success", analysis);
    }
}
